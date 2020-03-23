package com.jeffrey.example.demoapp.config;

import com.jeffrey.example.demoapp.receiver.DemoRabbitReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoRabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoRabbitConfig.class);

    public static final String ROUTING_KEY = "test.event.0";

    @Bean
    Queue queue() {
        // durable MUST be true otherwise the queue (and everything in it) will be deleted when message broker restart
        return new Queue("demoapp-exchange.demoapp-queue-0");
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange("demoapp-exchange-0");
    }

    @Bean
    Binding bindingTestEvent0(Queue queue, TopicExchange exchange) {
        // bind this queue behind the exchange topic and configure the specific routing key
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    SimpleMessageListenerContainer container(
            @Autowired DemoRabbitReceiver rabbitReceiver,
            @Autowired CachingConnectionFactory connectionFactory
    ) {
        // the queue will be created at run-time when this bean is instantiated
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue().getName());
        container.setMessageListener(rabbitReceiver);

        // Raising the number of concurrent consumers is recommendable
        // in order to scale the consumption of messages coming in from
        // a queue.
        //
        // However, note that any ordering guarantees are lost once multiple
        // consumers are registered.
        // In general, stick with 1 consumer for low-volume queues.
        container.setConcurrentConsumers(1);

        connectionFactory.setPublisherConfirms(true);
        connectionFactory.setPublisherReturns(true);

        return container;
    }

    @Bean
    @Qualifier("rabbitTemplate")
    public RabbitTemplate demoRabbitTemplate(
            @Autowired
            SimpleMessageListenerContainer container
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(container.getConnectionFactory());

        // Set the mandatory flag when sending messages
        // only applies if a returnCallback had been provided.
        rabbitTemplate.setMandatory(true);

        if (container.getConnectionFactory().isPublisherConfirms()) {
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                LOGGER.debug("correlationData: {}", correlationData);
                LOGGER.debug("ack: {}", ack);
                LOGGER.debug("cause: {}", cause);
            });
        }

        if (container.getConnectionFactory().isPublisherReturns()) {
            rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
                LOGGER.debug("message: {}", message);
                LOGGER.debug("replyCode: {}", replyCode);
                LOGGER.debug("replyText: {}", replyText);
                LOGGER.debug("exchange: {}", exchange);
                LOGGER.debug("routingKey: {}", routingKey);
            });
        }

        return rabbitTemplate;
    }

}
