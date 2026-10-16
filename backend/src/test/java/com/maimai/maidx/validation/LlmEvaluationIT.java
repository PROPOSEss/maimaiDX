package com.maimai.maidx.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.maimai.maidx.assistant.agent.*;
import com.maimai.maidx.assistant.config.AssistantProperties;
import com.maimai.maidx.assistant.context.*;
import com.maimai.maidx.assistant.dto.*;
import com.maimai.maidx.assistant.llm.*;
import com.maimai.maidx.assistant.service.AssistantAgentServiceImpl;
import com.maimai.maidx.assistant.tool.AssistantToolRegistry;
import com.maimai.maidx.repository.UserRepository;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only; deliberately excluded from *Test,*ValidationIT regression selectors. */
class LlmEvaluationIT extends ValidationTestSupport {
    @Autowired AssistantToolRegistry registry;
    @Autowired RuleBasedAssistantAgentPlanner rules;
    @Autowired AssistantAnswerGenerator answers;
    @Autowired UserRepository users;
    record Case(String id,String style,String question,List<String> tools,Map<String,Object> args,boolean legal) {}
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private int attempts;
    private long llmNanos;
    private boolean received;

    @Test void fixedRulesThenRealModelEvaluation() throws Exception {
        assertThat(System.getenv("MAIDX_EVAL_AUTHORIZED")).isEqualTo("true");
        List<Case> cases=mapper.readValue(getClass().getResourceAsStream("/llm-evaluation-cases.json"),new TypeReference<>() {});
        assertThat(cases).hasSize(25);
        mvp.importScores(1L,request(item(1,"98.0000"),item(2,"99.0000")));
        mvp.importScores(1L,request(item(1,"100.0000"),item(3,"97.0000")));
        mvp.importScores(2L,request(item(4,"100.5000")));
        jdbc.update("UPDATE score_record SET last_play_time=DATE_ADD('2026-01-01',INTERVAL id SECOND)");
        jdbc.update("UPDATE song_difficulty SET level_decimal=13.3 WHERE id=119");
        jdbc.update("UPDATE song_difficulty SET level_decimal=13.5 WHERE id=120");
        Path output=Path.of(Objects.requireNonNull(System.getenv("MAIDX_EVAL_RESULT")));
        try {
            for(Case c:cases) evaluate(c,rules,"RULE");
            save(output);
            AssistantProperties properties=new AssistantProperties();
            var ai=properties.getAi(); ai.setEnabled(true);
            ai.setApiKey(Objects.requireNonNull(System.getenv("MAIDX_EVAL_API_KEY")));
            ai.setBaseUrl(Objects.requireNonNull(System.getenv("MAIDX_EVAL_URL")));
            ai.setModel("qwen-plus");ai.setTimeoutMs(30000);ai.setMaxTokens(600);
            var client=new OpenAiCompatibleLlmClient(properties,mapper);
            // Test-only transport guard: one HTTP attempt per question, no automatic retry/redirect.
            ReflectionTestUtils.setField(client,"client",new OkHttpClient.Builder()
                    .callTimeout(Duration.ofSeconds(30)).connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(30)).retryOnConnectionFailure(false)
                    .followRedirects(false).followSslRedirects(false).build());
            LlmClient observed=new LlmClient(){
                public boolean isAvailable(){return client.isAvailable();}
                public String chat(String system,String question){
                    if(attempts>=25) throw new IllegalStateException("Evaluation request budget exhausted");
                    attempts++; long start=System.nanoTime();
                    try {String result=client.chat(system,question); received=true;return result;}
                    finally {llmNanos=System.nanoTime()-start;}
                }
            };
            var planner=new LlmAssistantAgentPlanner(observed,rules,registry,mapper);
            int consecutiveFallbacks=0;
            for(Case c:cases){
                evaluate(c,planner,"LLM");save(output);
                boolean fallback=Boolean.TRUE.equals(rows.get(rows.size()-1).get("fallbackUsed"));
                consecutiveFallbacks=fallback?consecutiveFallbacks+1:0;
                if(consecutiveFallbacks>=3) break; // avoid spending through repeated transport/parser failure
            }
        } finally {save(output);}
        assertThat(attempts).isLessThanOrEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_record",Integer.class)).isEqualTo(5);
    }

    private void save(Path path) throws Exception {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("externalRequestsAttempted",attempts,
                "requestedModel","qwen-plus","modelVersionFromApi","not captured by existing client",
                "caseSet","backend/src/test/resources/llm-evaluation-cases.json","results",rows));
    }

    private void evaluate(Case c,AssistantAgentPlanner delegate,String mode){
        long start=System.nanoTime();llmNanos=0;received=false;
        AssistantAgentPlan[] captured={null};
        AssistantAgentPlanner tracked=(message,context)->{captured[0]=delegate.plan(message,context);return captured[0];};
        var agent=new AssistantAgentServiceImpl(users,tracked,registry,answers);
        var request=new AssistantQueryRequest();request.setMessage(c.question());
        AssistantQueryResponse response=null;String error="";
        try {response=agent.query(new AssistantUserContext(1L,UserIdentitySource.DEVELOPMENT_HEADER),request,AssistantConversationContext.empty());}
        catch(Exception e){error=e.getClass().getSimpleName();}
        long total=System.nanoTime()-start;
        var plan=captured[0];var call=plan==null||plan.toolCalls().isEmpty()?null:plan.toolCalls().get(0);
        String tool=call==null?"NONE":call.toolName();Map<String,Object> actual=call==null?Map.of():call.arguments();
        boolean fallback=mode.equals("LLM")&&plan!=null&&plan.source().name().equals("RULE");
        String schema=call==null?"NOT_APPLICABLE":"PASS";
        if(call!=null)try{registry.validate(tool,actual);}catch(Exception e){schema="FAIL";}
        boolean toolCorrect=c.tools().contains(tool);
        boolean paramsCorrect=toolCorrect&&(tool.equals("NONE")?actual.isEmpty():equalArgs(normalize(tool,actual),normalize(tool,c.args())));
        boolean business=false;
        if(c.legal()&&toolCorrect&&paramsCorrect&&response!=null){
            try {checkBusiness(c,tool,response);business=true;} catch(AssertionError|RuntimeException e){error="BusinessResultMismatch";}
        }
        boolean safelyRejected=!c.legal()&&((response!=null&&tool.equals("NONE"))||!error.isEmpty());
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",c.id());row.put("mode",mode);row.put("question",c.question());row.put("legal",c.legal());
        row.put("expectedTools",c.tools());row.put("expectedArgs",c.args());row.put("actualTool",tool);row.put("actualArgs",actual);
        row.put("plannerSource",plan==null?"UNKNOWN":plan.source().name());row.put("fallbackUsed",fallback);
        row.put("schemaValidationResult",schema);row.put("toolExecutionResult",call==null?"NOT_EXECUTED":response!=null?"SUCCESS":schema.equals("FAIL")?"SCHEMA_REJECTED":"FAILED");
        row.put("toolCorrect",toolCorrect);row.put("paramsCorrect",paramsCorrect);row.put("businessCorrect",business);
        row.put("safelyRejected",safelyRejected);row.put("llmResponseReceived",received);
        row.put("llmLatencyMs",llmNanos/1_000_000.0);row.put("totalLatencyMs",total/1_000_000.0);
        row.put("failureReason",fallback?(received?"LLM_OUTPUT_REJECTED_FALLBACK":"LLM_TRANSPORT_OR_RESPONSE_ERROR_FALLBACK"):!error.isEmpty()?error:!toolCorrect?"WRONG_TOOL":!paramsCorrect?"WRONG_PARAMETERS":"");
        rows.add(row);
    }
    private Map<String,Object> normalize(String tool,Map<String,Object> raw){
        Map<String,Object> result=new LinkedHashMap<>();
        switch(tool){case "recent_scores"->result.put("limit",20);case "top_scores"->result.put("limit",30);
            case "random_recommendation"->result.put("count",5);case "training_advice"->result.put("adviceCount",3);
            case "song_search"->result.put("limit",10);default->{}}
        result.putAll(raw);return result;
    }
    private boolean equalArgs(Map<String,Object> a,Map<String,Object> b){
        if(!a.keySet().equals(b.keySet()))return false;
        for(String key:a.keySet()){
            Object x=a.get(key),y=b.get(key);
            if(x instanceof Number&&y instanceof Number){if(new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()))!=0)return false;}
            else if(!String.valueOf(x).trim().equalsIgnoreCase(String.valueOf(y).trim()))return false;
        }return true;
    }
    private void checkBusiness(Case c,String tool,AssistantQueryResponse response){
        Map<String,Object> gold=normalize(tool,c.args());
        if(tool.equals("recent_scores")||tool.equals("top_scores")){
            int limit=((Number)gold.get("limit")).intValue();
            String query=tool.equals("recent_scores")?
                "SELECT id FROM score_record WHERE user_id=1 ORDER BY last_play_time DESC,best_play_time DESC,created_at DESC,id DESC LIMIT "+limit:
                "SELECT id FROM (SELECT id,ra,achievement_rate,dx_score,last_play_time,ROW_NUMBER() OVER(PARTITION BY difficulty_id ORDER BY ra DESC,achievement_rate DESC,dx_score DESC,last_play_time DESC,id DESC) n FROM score_record WHERE user_id=1) x WHERE n=1 ORDER BY ra DESC,achievement_rate DESC,dx_score DESC,last_play_time DESC,id DESC LIMIT "+limit;
            assertThat(response.getScores().stream().map(AssistantQueryResponse.ScoreItem::getRecordId).toList()).containsExactlyElementsOf(jdbc.queryForList(query,Long.class));
            response.getScores().forEach(s->assertThat(jdbc.queryForObject("SELECT user_id FROM score_record WHERE id=?",Long.class,s.getRecordId())).isEqualTo(1L));
        }else if(tool.equals("random_recommendation")){
            BigDecimal min=new BigDecimal(gold.getOrDefault("minConstant",1).toString()),max=new BigDecimal(gold.getOrDefault("maxConstant",15.9).toString());
            int available=jdbc.queryForObject("SELECT COUNT(DISTINCT song_id) FROM song_difficulty WHERE level_decimal BETWEEN ? AND ?",Integer.class,min,max);
            assertThat(response.getRecommendations()).hasSize(Math.min(available,((Number)gold.get("count")).intValue()));
            Set<String> ids=new HashSet<>();response.getRecommendations().forEach(s->{assertThat(s.getConstant()).isBetween(min,max);ids.add(s.getSongId());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song_difficulty WHERE id=? AND level_decimal=?",Integer.class,s.getChartId(),s.getConstant())).isEqualTo(1);});
            assertThat(ids).hasSize(response.getRecommendations().size());
        }else if(tool.equals("training_advice")){
            assertThat(response.getSuggestions()).hasSize(((Number)gold.get("adviceCount")).intValue());
            assertThat(response.getEvidence()).hasSize(4);
            response.getEvidence().forEach(s->assertThat(jdbc.queryForObject("SELECT user_id FROM score_record WHERE id=?",Long.class,s.getRecordId())).isEqualTo(1L));
        }else if(tool.equals("song_search")){
            String keyword=gold.get("keyword").toString();int limit=((Number)gold.get("limit")).intValue();
            int matches=jdbc.queryForObject("SELECT COUNT(*) FROM song WHERE LOWER(title) LIKE LOWER(?)",Integer.class,"%"+keyword+"%");
            assertThat(response.getSongs()).hasSize(Math.min(limit,matches));
            response.getSongs().forEach(s->assertThat(s.getTitle().toLowerCase()).contains(keyword.toLowerCase()));
        }else throw new AssertionError("Unsupported legal tool");
    }
}
