package com.maimai.maidx.mq;

import com.maimai.maidx.service.ImportTaskStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScoreImportProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ImportTaskStatusService statusService;

    private ScoreImportProducer producer;

    @BeforeEach
    void setUp() {
        producer = new ScoreImportProducer(rabbitTemplate, statusService);
    }

    @Test
    void rabbitTemplateExceptionMarksTaskSendFailed() {
        doThrow(new IllegalStateException("send failed")).when(rabbitTemplate)
                .send(eq(RabbitMqConfig.SCORE_IMPORT_EXCHANGE), eq(RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY), any(), any(CorrelationData.class));

        producer.send(77L);

        verify(statusService).markSendFailed(eq(77L), any());
    }

    @Test
    void publisherNackMarksTaskSendFailed() {
        producer.configureCallbacks();
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> captor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        verify(rabbitTemplate).setConfirmCallback(captor.capture());

        captor.getValue().confirm(new CorrelationData("78"), false, "nack");

        verify(statusService).markSendFailed(78L, "nack");
    }

    @Test
    void publisherNackUsesCorrelationDataForEachTaskId() {
        producer.configureCallbacks();
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> captor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        verify(rabbitTemplate).setConfirmCallback(captor.capture());

        captor.getValue().confirm(new CorrelationData("81"), false, "nack-81");
        captor.getValue().confirm(new CorrelationData("82"), false, "nack-82");

        verify(statusService).markSendFailed(81L, "nack-81");
        verify(statusService).markSendFailed(82L, "nack-82");
    }

    @Test
    void consecutiveSendsCarryIndependentTaskIds() {
        producer.send(91L);
        producer.send(92L);

        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate, times(2)).send(eq(RabbitMqConfig.SCORE_IMPORT_EXCHANGE),
                eq(RabbitMqConfig.SCORE_IMPORT_ROUTING_KEY), any(), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CorrelationData::getId)
                .containsExactly("91", "92");
    }

    @Test
    void returnedMessageMarksTaskSendFailed() {
        producer.configureCallbacks();
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> captor = ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);
        verify(rabbitTemplate).setReturnsCallback(captor.capture());
        org.springframework.amqp.core.Message message = org.springframework.amqp.core.MessageBuilder
                .withBody("79".getBytes())
                .setCorrelationId("79")
                .build();

        captor.getValue().returnedMessage(new ReturnedMessage(message, 312, "NO_ROUTE", "ex", "rk"));

        verify(statusService).markSendFailed(eq(79L), any());
    }
}
