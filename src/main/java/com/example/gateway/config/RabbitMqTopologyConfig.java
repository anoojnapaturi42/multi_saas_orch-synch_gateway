package com.example.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqTopologyConfig {
    public static final String INGESTION_EXCHANGE = "ingestion.exchange";
    public static final String EVENTS_QUEUE = "workflow.events.queue";
    public static final String RETRY_QUEUE = "workflow.retry.queue";
    public static final String RETRY_EXCHANGE = "workflow.retry.exchange";
    public static final String DLQ_EXCHANGE = "workflow.dlq.exchange";
    public static final String DLQ = "workflow.dlq";
    public static final String EVENTS_KEY = "workflow.events";
    public static final String RETRY_KEY = "workflow.retry";
    public static final String DLQ_KEY = "workflow.dlq";

    @Bean DirectExchange ingestionExchange() { return new DirectExchange(INGESTION_EXCHANGE); }
    @Bean DirectExchange retryExchange() { return new DirectExchange(RETRY_EXCHANGE); }
    @Bean DirectExchange dlqExchange() { return new DirectExchange(DLQ_EXCHANGE); }

    @Bean Queue workflowEventsQueue() {
        return QueueBuilder.durable(EVENTS_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLQ_EXCHANGE, "x-dead-letter-routing-key", DLQ_KEY)).build();
    }

    @Bean Queue workflowRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", INGESTION_EXCHANGE, "x-dead-letter-routing-key", EVENTS_KEY)).build();
    }

    @Bean Queue workflowDlq() { return QueueBuilder.durable(DLQ).build(); }
    @Bean Binding eventsBinding() { return BindingBuilder.bind(workflowEventsQueue()).to(ingestionExchange()).with(EVENTS_KEY); }
    @Bean Binding retryBinding() { return BindingBuilder.bind(workflowRetryQueue()).to(retryExchange()).with(RETRY_KEY); }
    @Bean Binding dlqBinding() { return BindingBuilder.bind(workflowDlq()).to(dlqExchange()).with(DLQ_KEY); }
    @Bean Jackson2JsonMessageConverter rabbitJsonConverter() { return new Jackson2JsonMessageConverter(); }
    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory factory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }
}
