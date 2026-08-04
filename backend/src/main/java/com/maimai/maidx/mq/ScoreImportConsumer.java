package com.maimai.maidx.mq;

import com.maimai.maidx.service.AsyncImportExecutionService;
import com.maimai.maidx.service.ImportTaskStatusService;
import com.maimai.maidx.utils.SensitiveDataSanitizer;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreImportConsumer {

    private final AsyncImportExecutionService executionService;
    private final ImportTaskStatusService statusService;

    @RabbitListener(queues = RabbitMqConfig.SCORE_IMPORT_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long taskId = parseTaskId(message);
        if (taskId == null) {
            log.warn("Invalid score import message, ack directly");
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            executionService.execute(taskId);
            channel.basicAck(deliveryTag, false);
        } catch (AsyncImportBusinessException e) {
            String sanitizedMessage = SensitiveDataSanitizer.sanitize(e.getMessage());
            log.warn("Score import business failure: taskId={}, message={}", taskId, sanitizedMessage);
            statusService.markFailed(taskId, sanitizedMessage);
            channel.basicAck(deliveryTag, false);
        } catch (AsyncImportSystemException e) {
            log.warn("Score import system failure, reject to DLQ: taskId={}, error={}",
                    taskId, e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
            statusService.markFailed(taskId, e.getCause() == null ? e.getMessage() : e.getCause().getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
        }
    }

    private Long parseTaskId(Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            return Long.valueOf(body);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
