package com.example.demo.config;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitConfig.class);

//    @Bean
//    public ConnectionFactory defaultConnectionFactory() {
//        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
//        connectionFactory.setRequestedHeartBeat(15000);
//        return connectionFactory;
//    }
//
//    @Bean
//    public SimpleMessageListenerContainer messageListenerContainer(@Autowired ConnectionFactory connectionFactory) {
//        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
//        container.setConnectionFactory(connectionFactory);
//        container.setRecoveryInterval(15000);
//        return container;
//    }

    @RabbitListener(queues = "demo-exchange.demo-queue-2")
    public void onMessage(Message message, Channel channel) throws Exception {
        // handle the consuming of message
        LOGGER.debug("received message from queue-2: {}", message);

        LOGGER.debug("Received <" + message + ">");

        String exchangeTopic = message.getMessageProperties().getReceivedExchange();
        LOGGER.debug("Exchange: " + exchangeTopic);

        String queue = message.getMessageProperties().getConsumerQueue();
        LOGGER.debug("Queue: " + queue);

        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        LOGGER.debug("Routing Key: " + routingKey);

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        LOGGER.debug("Delivery Tag: " + deliveryTag);

        String contentType = message.getMessageProperties().getContentType();
        String contentEncoding = message.getMessageProperties().getContentEncoding();
        contentEncoding = contentEncoding == null ? "UTF-8" : contentEncoding;
        byte[] bytes = message.getBody();

        if ("text/plain".equals(contentType)) {
            //TODO: only support plain text in the message body at the moment
            String messageString = new String(bytes, contentEncoding);
            LOGGER.debug("message string: " + messageString);

            // TODO: add specific implementation based on the routing key

            try {
                // simulate I/O latency in the processing of message
                // put a hard-delay less than the pre-configured hystrix timeout otherwise hystrix will break the circuit
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }

            LOGGER.info("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
            // acknowledge message is processed and can be removed from queue
            channel.basicAck(deliveryTag, false);

        } else {
            throw new RuntimeException("un-supported content type: " + contentType);
        }
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
