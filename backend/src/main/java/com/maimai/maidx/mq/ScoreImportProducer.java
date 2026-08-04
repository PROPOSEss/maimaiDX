package com.maimai.maidx.mq;

import com.maimai.maidx.service.ImportTaskStatusService;
import com.maimai.maidx.utils.SensitiveDataSanitizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreImportProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ImportTaskStatusService statusService;

    @PostConstruct
    void configureCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                return;
            }
            Long taskId = parseTaskId(correlationData == null ? null : correlationData.getId());
            if (taskId != null) {
                statusService.markSendFailed(taskId, SensitiveDataSanitizer.sanitize(cause, 300));
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            Long taskId = parseTaskId(returned.getMessage().getMessageProperties().getCorrelationId());
            if (taskId != null) {
                statusService.markSendFailed(taskId, SensitiveDataSanitizer.sanitize("RabbitMQ message returned: replyCode="
                        + returned.getReplyCode() + ", replyText=" + returned.getReplyText(), 300));
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        send(event.taskId());
    }

    public void send(Long taskId) {
        try {
            byte[] body = String.valueOf(taskId).getBytes(StandardCharsets.UTF_8);
            Message message = MessageBuilder.withBody(body)
                    .setContentType("text/plain")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setCorrelationId(String.valueOf(taskId))
                    .build();
            rabbitTemplate.send(RabbitMqConfig.SCORE_IMPORT_EXCHANGE,
                    RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY,
                    message,
                    new CorrelationData(String.valueOf(taskId)));
        } catch (RuntimeException e) {
            log.warn("RabbitMQ send failed for import task: taskId={}, error={}", taskId, e.getClass().getSimpleName());
            statusService.markSendFailed(taskId, SensitiveDataSanitizer.sanitize(e.getMessage(), 300));
        }
    }

    private Long parseTaskId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
