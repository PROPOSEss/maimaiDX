package com.maimai.maidx.validation;

import com.maimai.maidx.dto.MvpDtos;
import com.maimai.maidx.service.MvpService;
import com.maimai.maidx.service.ScoreImportTaskService;
import com.maimai.maidx.dto.AsyncScoreImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.assertThat;

/** No test-managed transaction: assertions observe actual committed/rolled-back rows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cache.type=none", "assistant.ai.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "logging.level.com.maimai.maidx=WARN", "logging.level.root=WARN",
                "jwt.secret=validation-only-signing-key-at-least-32-bytes-long"})
@ActiveProfiles("validation")
abstract class ValidationTestSupport {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!"true".equals(System.getenv("MAIDX_VALIDATION_ENABLED"))) {
            throw new IllegalStateException("Use scripts/run-business-validation.py; development DB is forbidden");
        }
        String mysql = required("MAIDX_VALIDATION_MYSQL_PORT");
        String rabbit = required("MAIDX_VALIDATION_RABBIT_PORT");
        String redis = required("MAIDX_VALIDATION_REDIS_PORT");
        if ("3306".equals(mysql) || "5672".equals(rabbit) || "6379".equals(redis)) {
            throw new IllegalStateException("Refusing development ports");
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + mysql
                + "/maimaidx_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "validation-only-password");
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> rabbit);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> redis);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || !value.matches("[0-9]+")) throw new IllegalStateException("Missing isolated port: " + name);
        return value;
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MvpService mvp;
    @Autowired ScoreImportTaskService tasks;
    @Autowired ObjectMapper mapper;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired TestRestTemplate http;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void isolatedFixture() {
        listeners.stop();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimaidx_validation");
        assertThat(jdbc.queryForObject("SELECT run_id FROM validation_guard", String.class))
                .isEqualTo(System.getenv("MAIDX_VALIDATION_RUN"));
        admin.purgeQueue("maidx.score.import.queue", false);
        admin.purgeQueue("maidx.score.import.dlq", false);
        // Guard above is mandatory before any cleanup; only this run's disposable schema.
        for (String table : List.of("recommendation_item", "import_task", "score_record", "score_snapshot",
                "training_suggestion", "song_difficulty_vote", "player_ability", "player_bind",
                "song_feature", "song_difficulty", "song", "user")) {
            jdbc.update("DELETE FROM `" + table + "`");
        }
        jdbc.update("INSERT INTO `user`(id,openid,nickname) VALUES (1,'validation-one','One'),(2,'validation-two','Two')");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int i = 1; i <= 120; i++) {
                jdbc.update("INSERT INTO song(id,song_id,title,artist,is_new) VALUES (?,?,?,?,?)",
                        i, code(i), "Validation Song " + i, "Validation Artist", i % 2);
                jdbc.update("INSERT INTO song_difficulty(id,song_id,difficulty,level,level_decimal) VALUES (?,?,3,13,13.0)", i, i);
            }
        });
    }

    static String code(int i) { return String.format("v%03d", i); }

    static MvpDtos.ScoreImportItem item(int song, String achievement) {
        MvpDtos.ScoreImportItem item = new MvpDtos.ScoreImportItem();
        item.setSongId(code(song));
        item.setDifficulty(3);
        item.setAchievement(new BigDecimal(achievement));
        item.setDxScore(900000);
        return item;
    }

    static MvpDtos.ScoreImportRequest request(MvpDtos.ScoreImportItem... items) {
        MvpDtos.ScoreImportRequest request = new MvpDtos.ScoreImportRequest();
        request.setSource("isolated_validation");
        request.setRating(12000);
        request.setRecords(new ArrayList<>(List.of(items)));
        return request;
    }

    static AsyncScoreImportRequest async(String id, MvpDtos.ScoreImportItem... items) {
        AsyncScoreImportRequest request = new AsyncScoreImportRequest();
        request.setRequestId(id);
        request.setSource("isolated_validation");
        request.setRecords(new ArrayList<>(List.of(items)));
        return request;
    }

    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class); }

    void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("condition within 15 seconds").isTrue();
    }
}
