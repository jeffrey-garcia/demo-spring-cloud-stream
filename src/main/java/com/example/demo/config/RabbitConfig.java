package com.example.demo.config;


import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
public class RabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitConfig.class);

    @RabbitListener(queues = "demo-exchange.demo-queue-2")
    public void onMessage(Message message, Channel channel) {
        // handle the consuming of message
        LOGGER.debug("received message from queue-2: {}", message);
    }

    @Bean
    Queue queue() {
        // durable MUST be true otherwise the queue (and everything in it) will be deleted when message broker restart
        return new Queue("demo-exchange.demo-queue-2");
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange("demo-exchange");
    }

    @Bean
    Binding bindingTestEvent1(Queue queue, TopicExchange exchange) {
        // bind this queue behind the exchange topic and configure the specific routing key
        return BindingBuilder.bind(queue).to(exchange).with("test.event.2");
    }


}
