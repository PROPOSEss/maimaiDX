package com.maimai.maidx.mq;

import com.maimai.maidx.service.AsyncImportExecutionService;
import com.maimai.maidx.service.ImportTaskStatusService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScoreImportConsumerTest {

    private ScoreImportConsumer consumer;

    @Mock
    private AsyncImportExecutionService executionService;

    @Mock
    private ImportTaskStatusService statusService;

    @Mock
    private Channel channel;

    @BeforeEach
    void setUp() {
        consumer = new ScoreImportConsumer(executionService, statusService);
    }

    @Test
    void normalConsumptionAcksMessage() throws Exception {
        Message message = message("101", 9L);

        consumer.onMessage(message, channel);

        verify(executionService).execute(101L);
        verify(channel).basicAck(9L, false);
    }

    @Test
    void businessFailureMarksFailedAndAcks() throws Exception {
        Message message = message("102", 10L);
        doThrow(new AsyncImportBusinessException("歌曲不存在")).when(executionService).execute(102L);

        consumer.onMessage(message, channel);

        verify(statusService).markFailed(102L, "歌曲不存在");
        verify(channel).basicAck(10L, false);
    }

    @Test
    void systemFailureMarksFailedAndRejectsToDlq() throws Exception {
        Message message = message("103", 11L);
        doThrow(new AsyncImportSystemException("系统异常", new IllegalStateException("db down")))
                .when(executionService).execute(103L);

        consumer.onMessage(message, channel);

        verify(statusService).markFailed(103L, "IllegalStateException");
        verify(channel).basicReject(11L, false);
    }

    @Test
    void invalidMessageIsAcked() throws Exception {
        Message message = message("not-a-number", 12L);

        consumer.onMessage(message, channel);

        verify(channel).basicAck(12L, false);
    }

    private Message message(String body, long deliveryTag) {
        Message message = MessageBuilder.withBody(body.getBytes()).build();
        message.getMessageProperties().setDeliveryTag(deliveryTag);
        return message;
    }
}
