package com.maimai.maidx.assistant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.dto.TrainingProfile;
import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.assistant.enums.TrainingFocusType;
import com.maimai.maidx.assistant.llm.LlmClient;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingAdviceTool extends AssistantToolSupport implements AssistantTool {

    private static final int RECENT_LIMIT = 20;
    private static final int TOP_LIMIT = 10;
    private static final int CANDIDATE_LIMIT = 8;
    private static final int DEFAULT_ADVICE_COUNT = 3;
    private static final int MAX_ADVICE_COUNT = 5;
    private static final Set<String> ADVICE_ROOT_FIELDS = Set.of("focusTypes");
    private static final List<TrainingFocusType> DEFAULT_FOCUS_TYPES = List.of(
            TrainingFocusType.IMPROVEMENT_CANDIDATE,
            TrainingFocusType.CONSTANT_STABILITY,
            TrainingFocusType.RECENT_CONSISTENCY,
            TrainingFocusType.B50_EDGE,
            TrainingFocusType.TOP_SCORE_STABILITY);
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You select maimaiDX training focus types from the provided aggregate profile JSON only.
            Return exactly one JSON object. Do not return Markdown or extra text.
            Required format:
            {"focusTypes":["IMPROVEMENT_CANDIDATE","CONSTANT_STABILITY","RECENT_CONSISTENCY"]}
            Rules:
            - focusTypes must contain exactly %d distinct items.
            - Allowed values: IMPROVEMENT_CANDIDATE, CONSTANT_STABILITY, RECENT_CONSISTENCY, B50_EDGE, TOP_SCORE_STABILITY.
            - Do not output summary, title, strategy, action, reason, evidence, song names, chart identifiers, constants, achievements, RA values, dates, thresholds, SQL, database tables, or user identifiers.
            - Do not create facts that are absent from the aggregate profile.
            - Do not combine fields from different records or infer minimum scores, maximum scores, bottleneck songs, or unprovided thresholds such as 13.1 or 14.4.
            - When uncertain, select only general focus types.
            """;

    private final ScoreRecordRepository scoreRecordRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final SongService songService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public IntentType supportIntent() {
        return IntentType.TRAINING_ADVICE;
    }

    @Override
    public AssistantQueryResponse execute(Long userId, ParsedIntent intent) {
        TrainingContext context = buildTrainingContext(userId);
        if (context.recentRecords().isEmpty()) {
            return noScoreResponse(userId, intent);
        }

        AssistantQueryResponse response = null;
        if (llmClient.isAvailable()) {
            try {
                response = buildLlmResponse(userId, intent, context);
            } catch (Exception e) {
                log.warn("LLM训练建议生成失败，已降级为规则建议: {}", e.getClass().getSimpleName());
            }
        }
        if (response == null) {
            response = buildRuleResponse(userId, intent, context);
        }
        response.setEvidence(context.evidence());
        return response;
    }

    TrainingContext buildTrainingContext(Long userId) {
        List<ScoreRecord> recentRecords = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .orderByDesc(ScoreRecord::getLastPlayTime)
                .orderByDesc(ScoreRecord::getBestPlayTime)
                .orderByDesc(ScoreRecord::getCreatedAt)
                .last("LIMIT " + RECENT_LIMIT));

        List<ScoreRecord> topSourceRecords = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .isNotNull(ScoreRecord::getDifficultyId)
                .isNotNull(ScoreRecord::getAchievementRate)
                .orderByDesc(ScoreRecord::getAchievementRate)
                .orderByDesc(ScoreRecord::getLastPlayTime)
                .last("LIMIT 80"));

        List<ScoreRecord> topRecords = topSourceRecords.stream()
                .collect(Collectors.toMap(
                        ScoreRecord::getDifficultyId,
                        Function.identity(),
                        (left, right) -> topScoreComparator().compare(left, right) >= 0 ? left : right,
                        LinkedHashMap::new))
                .values()
                .stream()
                .sorted(topScoreComparator().reversed())
                .limit(TOP_LIMIT)
                .toList();

        List<ScoreRecord> combined = new ArrayList<>();
        combined.addAll(recentRecords);
        combined.addAll(topRecords);
        Map<Long, SongDifficulty> chartMap = loadChartMap(combined);
        Map<Long, Song> songMap = loadSongMap(combined, chartMap);

        List<AssistantQueryResponse.ScoreItem> evidence = recentRecords.stream()
                .limit(8)
                .map(record -> toScoreItem(record, songMap, chartMap))
                .toList();

        TrainingProfile profile = new TrainingProfile();
        profile.setRecentCount(recentRecords.size());
        profile.setAverageAchievement(averageAchievement(recentRecords));
        profile.setMainConstantMin(mainConstant(recentRecords, chartMap, true));
        profile.setMainConstantMax(mainConstant(recentRecords, chartMap, false));
        profile.setRecentTrend(recentTrend(recentRecords));
        profile.setB50EdgeRa(loadB50EdgeRa(userId));
        profile.setRecentScores(toTrainingItems(recentRecords, songMap, chartMap, RECENT_LIMIT));
        profile.setTopScores(toTrainingItems(topRecords, songMap, chartMap, TOP_LIMIT));
        profile.setImprovementCandidates(toTrainingItems(improvementCandidates(recentRecords), songMap, chartMap, CANDIDATE_LIMIT));
        return new TrainingContext(profile, recentRecords, evidence);
    }

    private AssistantQueryResponse buildLlmResponse(Long userId, ParsedIntent intent, TrainingContext context) throws Exception {
        int adviceCount = resolveAdviceCount(intent);
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(adviceCount);
        TrainingPlanInput planInput = toTrainingPlanInput(context.profile());
        String content = llmClient.chat(systemPrompt, objectMapper.writeValueAsString(planInput));
        List<TrainingFocusType> focusTypes = parseFocusTypes(content, adviceCount);
        return buildDeterministicResponse(userId, intent, context.profile(), focusTypes, AdviceSource.LLM);
    }

    private List<TrainingFocusType> parseFocusTypes(String content, int adviceCount) throws Exception {
        JsonNode node = objectMapper.readTree(content.trim());
        if (!node.isObject()) {
            throw new IllegalArgumentException("advice must be object");
        }
        validateFields(node, ADVICE_ROOT_FIELDS);
        JsonNode focusTypesNode = node.path("focusTypes");
        if (!focusTypesNode.isArray()) {
            throw new IllegalArgumentException("focusTypes must be array");
        }
        List<TrainingFocusType> focusTypes = new ArrayList<>();
        for (JsonNode focusTypeNode : focusTypesNode) {
            if (!focusTypeNode.isTextual()) {
                throw new IllegalArgumentException("focusType must be text");
            }
            TrainingFocusType focusType = TrainingFocusType.valueOf(focusTypeNode.asText());
            if (focusTypes.contains(focusType)) {
                throw new IllegalArgumentException("duplicate focusType");
            }
            if (focusTypes.size() < adviceCount) {
                focusTypes.add(focusType);
            }
        }
        if (focusTypes.size() < adviceCount) {
            throw new IllegalArgumentException("invalid advice count");
        }
        return focusTypes;
    }

    private void validateFields(JsonNode node, Set<String> allowedFields) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("unsupported advice field");
            }
        });
    }

    private AssistantQueryResponse buildRuleResponse(Long userId, ParsedIntent intent, TrainingContext context) {
        int adviceCount = resolveAdviceCount(intent);
        return buildDeterministicResponse(
                userId,
                intent,
                context.profile(),
                DEFAULT_FOCUS_TYPES.subList(0, adviceCount),
                AdviceSource.RULE);
    }

    private AssistantQueryResponse buildDeterministicResponse(Long userId,
                                                              ParsedIntent intent,
                                                              TrainingProfile profile,
                                                              List<TrainingFocusType> focusTypes,
                                                              AdviceSource adviceSource) {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userId);
        response.setIntent(IntentType.TRAINING_ADVICE);
        response.setParsedIntent(intent);
        response.setAdviceSource(adviceSource);
        response.setAnswer(adviceSource == AdviceSource.LLM
                ? "已根据真实成绩画像和模型选择的训练方向生成建议。"
                : "已根据最近" + profile.getRecentCount() + "条真实成绩生成规则训练建议。");
        response.setSuggestions(focusTypes.stream()
                .map(focusType -> suggestionForFocus(focusType, profile))
                .toList());
        return response;
    }

    private AssistantQueryResponse.TrainingSuggestionItem suggestionForFocus(TrainingFocusType focusType,
                                                                              TrainingProfile profile) {
        return switch (focusType) {
            case IMPROVEMENT_CANDIDATE -> suggestion(
                    focusType,
                    "优先复练接近提升线的谱面",
                    profile.getImprovementCandidates().isEmpty()
                            ? "最近成绩中暂未发现处于可提升区间的谱面。"
                            : "最近成绩中有 " + profile.getImprovementCandidates().size() + " 张谱面处于可提升区间。",
                    "从后端返回的 evidence 中选择接近目标线的谱面，优先减少失误并提高稳定性。");
            case CONSTANT_STABILITY -> suggestion(
                    focusType,
                    "围绕常打定数稳定练习",
                    "最近主要游玩定数区间为 " + valueOrUnknown(profile.getMainConstantMin())
                            + " 到 " + valueOrUnknown(profile.getMainConstantMax()) + "。",
                    "先在常打区间内稳定发挥，再逐步增加挑战难度。");
            case RECENT_CONSISTENCY -> suggestion(
                    focusType,
                    "关注近期发挥稳定性",
                    "最近成绩趋势由后端计算为：" + profile.getRecentTrend() + "。",
                    "复盘低于近期平均水平的成绩，优先修正重复出现的失误。");
            case B50_EDGE -> suggestion(
                    focusType,
                    "关注B50边缘成绩",
                    profile.getB50EdgeRa() == null
                            ? "当前成绩画像中没有可用的B50边缘RA。"
                            : "当前B50边缘RA为 " + profile.getB50EdgeRa() + "。",
                    "优先选择预期提升能够接近或超过边缘成绩的谱面进行练习。");
            case TOP_SCORE_STABILITY -> suggestion(
                    focusType,
                    "保持高分谱面的稳定性",
                    "后端从真实成绩中选取了 " + profile.getTopScores().size() + " 张高分谱面作为分析样本。",
                    "先保持已有高分谱面的稳定发挥，再安排新的冲分目标。");
        };
    }

    private TrainingPlanInput toTrainingPlanInput(TrainingProfile profile) {
        return new TrainingPlanInput(
                profile.getRecentCount(),
                profile.getAverageAchievement(),
                profile.getMainConstantMin(),
                profile.getMainConstantMax(),
                profile.getRecentTrend(),
                profile.getB50EdgeRa(),
                profile.getImprovementCandidates().size(),
                profile.getTopScores().size());
    }

    private int resolveAdviceCount(ParsedIntent intent) {
        if (intent == null || intent.getAdviceCount() == null) {
            return DEFAULT_ADVICE_COUNT;
        }
        return Math.max(1, Math.min(intent.getAdviceCount(), MAX_ADVICE_COUNT));
    }

    private AssistantQueryResponse noScoreResponse(Long userId, ParsedIntent intent) {
        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userId);
        response.setIntent(IntentType.TRAINING_ADVICE);
        response.setParsedIntent(intent);
        response.setAdviceSource(AdviceSource.RULE);
        response.setAnswer("当前用户暂无可分析成绩，请先导入成绩 JSON 后再生成训练建议。");
        response.getSuggestions().add(suggestion(
                TrainingFocusType.IMPROVEMENT_CANDIDATE,
                "先导入成绩",
                "训练建议需要基于真实成绩记录生成。",
                "先使用成绩导入接口导入一份成绩 JSON。"));
        return response;
    }

    private List<ScoreRecord> improvementCandidates(List<ScoreRecord> records) {
        return records.stream()
                .filter(record -> record.getAchievementRate() != null)
                .filter(record -> record.getAchievementRate().compareTo(new BigDecimal("97.000")) >= 0)
                .filter(record -> record.getAchievementRate().compareTo(new BigDecimal("100.500")) < 0)
                .sorted(Comparator
                        .comparing((ScoreRecord record) -> improvementDistance(record.getAchievementRate()))
                        .thenComparing(this::playedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(CANDIDATE_LIMIT)
                .toList();
    }

    private BigDecimal improvementDistance(BigDecimal achievement) {
        BigDecimal target = achievement.compareTo(new BigDecimal("99.800")) >= 0
                ? new BigDecimal("100.500")
                : achievement.compareTo(new BigDecimal("99.000")) >= 0
                ? new BigDecimal("100.000")
                : new BigDecimal("99.000");
        return target.subtract(achievement).abs();
    }

    private List<TrainingProfile.TrainingScoreItem> toTrainingItems(List<ScoreRecord> records,
                                                                    Map<Long, Song> songMap,
                                                                    Map<Long, SongDifficulty> chartMap,
                                                                    int limit) {
        return records.stream()
                .limit(limit)
                .map(record -> toTrainingItem(record, songMap, chartMap))
                .toList();
    }

    private TrainingProfile.TrainingScoreItem toTrainingItem(ScoreRecord record,
                                                             Map<Long, Song> songMap,
                                                             Map<Long, SongDifficulty> chartMap) {
        SongDifficulty chart = chartMap.get(record.getDifficultyId());
        Song song = resolveSong(record, chart, songMap);
        TrainingProfile.TrainingScoreItem item = new TrainingProfile.TrainingScoreItem();
        item.setSongId(song == null ? null : song.getSongId());
        item.setSongName(song == null ? null : song.getTitle());
        item.setDifficulty(chart == null ? null : chart.getDifficulty());
        item.setConstant(chart == null ? null : chart.getLevelDecimal());
        item.setAchievement(record.getAchievementRate());
        item.setRa(record.getRa());
        item.setPlayedAt(playedAt(record));
        return item;
    }

    private Map<Long, SongDifficulty> loadChartMap(List<ScoreRecord> records) {
        List<Long> chartIds = records.stream()
                .map(ScoreRecord::getDifficultyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chartIds.isEmpty()) {
            return Map.of();
        }
        return songDifficultyRepository.selectBatchIds(chartIds).stream()
                .collect(Collectors.toMap(SongDifficulty::getId, Function.identity()));
    }

    private Map<Long, Song> loadSongMap(List<ScoreRecord> records, Map<Long, SongDifficulty> chartMap) {
        List<Long> songIds = records.stream()
                .map(record -> record.getSongId() != null
                        ? record.getSongId()
                        : chartMap.get(record.getDifficultyId()) == null ? null : chartMap.get(record.getDifficultyId()).getSongId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (songIds.isEmpty()) {
            return Map.of();
        }
        return songService.listByIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
    }

    private Song resolveSong(ScoreRecord record, SongDifficulty chart, Map<Long, Song> songMap) {
        if (record.getSongId() != null && songMap.containsKey(record.getSongId())) {
            return songMap.get(record.getSongId());
        }
        return chart == null ? null : songMap.get(chart.getSongId());
    }

    private BigDecimal averageAchievement(List<ScoreRecord> records) {
        List<BigDecimal> achievements = records.stream()
                .map(ScoreRecord::getAchievementRate)
                .filter(Objects::nonNull)
                .toList();
        if (achievements.isEmpty()) {
            return null;
        }
        BigDecimal sum = achievements.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(achievements.size()), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal mainConstant(List<ScoreRecord> records, Map<Long, SongDifficulty> chartMap, boolean min) {
        return records.stream()
                .map(record -> chartMap.get(record.getDifficultyId()))
                .filter(Objects::nonNull)
                .map(SongDifficulty::getLevelDecimal)
                .filter(Objects::nonNull)
                .min(min ? Comparator.naturalOrder() : Comparator.reverseOrder())
                .orElse(null);
    }

    private String recentTrend(List<ScoreRecord> records) {
        if (records.size() < 6) {
            return "样本较少";
        }
        BigDecimal latest = averageAchievement(records.subList(0, Math.min(5, records.size())));
        BigDecimal previous = averageAchievement(records.subList(Math.min(5, records.size()), Math.min(10, records.size())));
        if (latest == null || previous == null) {
            return "样本不足";
        }
        int comparison = latest.subtract(previous).compareTo(new BigDecimal("0.200"));
        if (comparison > 0) {
            return "最近达成率略有上升";
        }
        if (latest.subtract(previous).compareTo(new BigDecimal("-0.200")) < 0) {
            return "最近达成率略有下降";
        }
        return "最近达成率较稳定";
    }

    private Integer loadB50EdgeRa(Long userId) {
        List<ScoreRecord> records = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .eq(ScoreRecord::getIsB50, 1)
                .isNotNull(ScoreRecord::getRa)
                .orderByAsc(ScoreRecord::getRa)
                .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0).getRa();
    }

    private AssistantQueryResponse.TrainingSuggestionItem suggestion(TrainingFocusType focusType,
                                                                     String title,
                                                                     String reason,
                                                                     String action) {
        AssistantQueryResponse.TrainingSuggestionItem item = new AssistantQueryResponse.TrainingSuggestionItem();
        item.setFocusType(focusType);
        item.setTitle(title);
        item.setReason(reason);
        item.setAction(action);
        return item;
    }

    private String valueOrUnknown(BigDecimal value) {
        return value == null ? "未知" : value.toPlainString();
    }

    record TrainingContext(TrainingProfile profile,
                           List<ScoreRecord> recentRecords,
                           List<AssistantQueryResponse.ScoreItem> evidence) {
    }

    private record TrainingPlanInput(Integer recentCount,
                                     BigDecimal averageAchievement,
                                     BigDecimal mainConstantMin,
                                     BigDecimal mainConstantMax,
                                     String recentTrend,
                                     Integer b50EdgeRa,
                                     Integer improvementCandidateCount,
                                     Integer topScoreCount) {
    }
}
