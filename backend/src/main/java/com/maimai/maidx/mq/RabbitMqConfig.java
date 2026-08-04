package com.maimai.maidx.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String SCORE_IMPORT_EXCHANGE = "maidx.score.import.exchange";
    public static final String SCORE_IMPORT_QUEUE = "maidx.score.import.queue";
    public static final String SCORE_IMPORT_ROUTING_KEY = "score.import";

    public static final String SCORE_IMPORT_DLX = "maidx.score.import.dlx";
    public static final String SCORE_IMPORT_DLQ = "maidx.score.import.dlq";
    public static final String SCORE_IMPORT_DEAD_ROUTING_KEY = "score.import.dead";

    @Bean
    public DirectExchange scoreImportExchange() {
        return new DirectExchange(SCORE_IMPORT_EXCHANGE, true, false);
    }

    @Bean
    public Queue scoreImportQueue() {
        return QueueBuilder.durable(SCORE_IMPORT_QUEUE)
                .deadLetterExchange(SCORE_IMPORT_DLX)
                .deadLetterRoutingKey(SCORE_IMPORT_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding scoreImportBinding(Queue scoreImportQueue, DirectExchange scoreImportExchange) {
        return BindingBuilder.bind(scoreImportQueue)
                .to(scoreImportExchange)
                .with(SCORE_IMPORT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange scoreImportDeadLetterExchange() {
        return new DirectExchange(SCORE_IMPORT_DLX, true, false);
    }

    @Bean
    public Queue scoreImportDeadLetterQueue() {
        return QueueBuilder.durable(SCORE_IMPORT_DLQ).build();
    }

    @Bean
    public Binding scoreImportDeadLetterBinding(Queue scoreImportDeadLetterQueue,
                                                DirectExchange scoreImportDeadLetterExchange) {
        return BindingBuilder.bind(scoreImportDeadLetterQueue)
                .to(scoreImportDeadLetterExchange)
                .with(SCORE_IMPORT_DEAD_ROUTING_KEY);
    }
}
