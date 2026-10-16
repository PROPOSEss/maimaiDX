package com.maimai.maidx.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.maimai.maidx.mq.RabbitMqConfig;
import com.maimai.maidx.mq.ScoreImportProducer;
import com.maimai.maidx.service.ImportTaskStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.ServerSocket;
import static org.assertj.core.api.Assertions.*;

class RabbitImportValidationIT extends ValidationTestSupport {
    @Autowired ImportTaskStatusService statuses;

    String status(long id) {
        return jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, id);
    }

    void success(long id, int rows) throws Exception {
        listeners.start();
        await(() -> "SUCCESS".equals(status(id)));
        var task = tasks.getTask(1L, id);
        assertThat(task.getSnapshotId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_record WHERE snapshot_id=?", Integer.class,
                task.getSnapshotId())).isEqualTo(rows);
    }

    @Test
    void httpSubmissionReachesBrokerAndConsumerAndCanBeQueried() throws Exception {
        // TestRestTemplate's injected root URI already includes server.servlet.context-path (/api).
        JsonNode submitted = http.postForObject("/player/import/async?userId=1",
                async("http-normal", item(1, "98.00"), item(2, "99.00")), JsonNode.class);
        assertThat(submitted.path("code").asInt()).isEqualTo(200);
        long id = submitted.path("data").path("taskId").asLong();
        assertThat(id).isPositive();
        assertThat(status(id)).isEqualTo("PENDING");
        await(() -> admin.getQueueInfo(RabbitMqConfig.SCORE_IMPORT_QUEUE).getMessageCount() == 1);
        success(id, 2);
        JsonNode result = http.getForObject("/player/import/tasks/" + id + "?userId=1", JsonNode.class);
        assertThat(result.path("data").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("data").path("snapshotId").asLong()).isPositive();
        assertThat(tasks.getTaskByRequest(1L, "http-normal").getTaskId()).isEqualTo(id);
    }

    @Test
    void repeatedSubmissionAndDuplicateMessagesDoNotDuplicateCommittedResults() throws Exception {
        var input = async("duplicate", item(1, "99.00"));
        long id = tasks.submit(1L, input).getTaskId();
        assertThat(tasks.submit(1L, input).getTaskId()).isEqualTo(id);
        success(id, 1);
        listeners.stop();
        rabbit.convertAndSend(RabbitMqConfig.SCORE_IMPORT_EXCHANGE,
                RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY, String.valueOf(id));
        rabbit.convertAndSend(RabbitMqConfig.SCORE_IMPORT_EXCHANGE,
                RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY, String.valueOf(id));
        listeners.start();
        await(() -> admin.getQueueInfo(RabbitMqConfig.SCORE_IMPORT_QUEUE).getMessageCount() == 0);
        listeners.stop();
        assertThat(count("import_task")).isEqualTo(1);
        assertThat(count("score_snapshot")).isEqualTo(1);
        assertThat(count("score_record")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM import_task WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void businessFailureRollsBackAndRecordsQueryableFailureWithoutDeadLetter() throws Exception {
        var bad = item(2, "99.00");
        bad.setSongId("missing-validation-song");
        long id = tasks.submit(1L, async("business-error", item(1, "99.00"), bad)).getTaskId();
        listeners.start();
        await(() -> "FAILED".equals(status(id)));
        listeners.stop();
        assertThat(tasks.getTask(1L, id).getErrorMessage()).contains("歌曲不存在");
        assertThat(count("score_snapshot")).isZero();
        assertThat(count("score_record")).isZero();
        assertThat(admin.getQueueInfo(RabbitMqConfig.SCORE_IMPORT_DLQ).getMessageCount()).isZero();
    }

    @Test
    void unroutableMessageIsRecordedAndManualRetryCompletesOriginalTask() throws Exception {
        Binding binding = new Binding(RabbitMqConfig.SCORE_IMPORT_QUEUE, Binding.DestinationType.QUEUE,
                RabbitMqConfig.SCORE_IMPORT_EXCHANGE, RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY, null);
        long id;
        admin.removeBinding(binding);
        try {
            id = tasks.submit(1L, async("returned-message", item(1, "99.00"))).getTaskId();
            await(() -> "SEND_FAILED".equals(status(id)));
            assertThat(tasks.getTask(1L, id).getErrorMessage()).contains("returned");
            assertThat(count("score_snapshot")).isZero();
        } finally {
            admin.declareBinding(binding);
        }
        assertThat(tasks.retrySendFailedTask(1L, id).getTaskId()).isEqualTo(id);
        success(id, 1);
        assertThat(count("import_task")).isEqualTo(1);
    }

    @Test
    void realTransportFailureCanBeRetriedThroughHealthyBroker() throws Exception {
        String payload = mapper.writeValueAsString(async("transport-failure", item(1, "99.00")));
        jdbc.update("INSERT INTO import_task(request_id,user_id,status,request_payload) VALUES ('transport-failure',1,'PENDING',?)", payload);
        long id = jdbc.queryForObject("SELECT id FROM import_task WHERE request_id='transport-failure'", Long.class);
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        CachingConnectionFactory unavailable = new CachingConnectionFactory("127.0.0.1", closedPort);
        unavailable.setConnectionTimeout(1000);
        try {
            new ScoreImportProducer(new RabbitTemplate(unavailable), statuses).send(id);
        } finally {
            unavailable.destroy();
        }
        assertThat(status(id)).isEqualTo("SEND_FAILED");
        assertThat(tasks.getTask(1L, id).getErrorMessage()).isNotBlank();
        tasks.retrySendFailedTask(1L, id);
        success(id, 1);
    }

    @Test
    void databaseSystemFailureProducesActualDeadLetterAndNoPartialResults() throws Exception {
        jdbc.execute("CREATE TRIGGER validation_fail BEFORE INSERT ON score_record FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='validation injected system failure'");
        try {
            long id = tasks.submit(1L, async("system-error", item(1, "99.00"))).getTaskId();
            listeners.start();
            await(() -> "FAILED".equals(status(id)));
            await(() -> admin.getQueueInfo(RabbitMqConfig.SCORE_IMPORT_DLQ).getMessageCount() == 1);
            listeners.stop();
            var message = rabbit.receive(RabbitMqConfig.SCORE_IMPORT_DLQ, 2000);
            assertThat(message).isNotNull();
            assertThat(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(String.valueOf(id));
            assertThat(message.getMessageProperties().getHeaders()).containsKey("x-death");
            assertThat(tasks.getTask(1L, id).getErrorMessage()).isNotBlank();
            assertThat(count("score_snapshot")).isZero();
            assertThat(count("score_record")).isZero();
            assertThatThrownBy(() -> tasks.retrySendFailedTask(1L, id))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("只有SEND_FAILED");
        } finally {
            jdbc.execute("DROP TRIGGER validation_fail");
        }
    }

    @Test
    void rollbackOfTaskCreationPublishesNoBrokerMessage() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            tasks.submit(1L, async("rolled-back-task", item(1, "99.00")));
            throw new IllegalStateException("validation rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("import_task")).isZero();
        assertThat(admin.getQueueInfo(RabbitMqConfig.SCORE_IMPORT_QUEUE).getMessageCount()).isZero();
    }

    @Test
    void userScopedRequestsAndChangedPayloadUseOriginalCommittedTask() throws Exception {
        long first = tasks.submit(1L, async("shared-id", item(1, "98.00"))).getTaskId();
        long second = tasks.submit(2L, async("shared-id", item(2, "99.00"))).getTaskId();
        assertThat(first).isNotEqualTo(second);
        assertThat(tasks.submit(1L, async("shared-id", item(3, "100.00"))).getTaskId()).isEqualTo(first);
        assertThatThrownBy(() -> tasks.getTask(2L, first)).isInstanceOf(IllegalArgumentException.class);
        listeners.start();
        await(() -> "SUCCESS".equals(status(first)) && "SUCCESS".equals(status(second)));
        listeners.stop();
        assertThat(count("import_task")).isEqualTo(2);
        assertThat(count("score_snapshot")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT difficulty_id FROM score_record WHERE user_id=1", Long.class)).containsExactly(1L);
        assertThat(jdbc.queryForList("SELECT difficulty_id FROM score_record WHERE user_id=2", Long.class)).containsExactly(2L);
    }

    @Test
    void wrongPayloadShapeIsBusinessFailureWithNoPartialWrite() throws Exception {
        jdbc.update("INSERT INTO import_task(request_id,user_id,status,request_payload) VALUES ('bad-shape',1,'PENDING',?)",
                mapper.writeValueAsString("not-an-import-object"));
        long id = jdbc.queryForObject("SELECT id FROM import_task WHERE request_id='bad-shape'", Long.class);
        rabbit.convertAndSend(RabbitMqConfig.SCORE_IMPORT_EXCHANGE,
                RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY, String.valueOf(id));
        listeners.start();
        await(() -> "FAILED".equals(status(id)));
        listeners.stop();
        assertThat(tasks.getTask(1L, id).getErrorMessage()).contains("payload无法反序列化");
        assertThat(count("score_record")).isZero();
        assertThat(count("score_snapshot")).isZero();
    }
}
