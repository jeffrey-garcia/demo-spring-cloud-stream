package com.jeffrey.example.demoapp.receiver;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;

@Component
public class DemoRabbitReceiver implements ChannelAwareMessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoRabbitReceiver.class);

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        // handle the consuming of message
        LOGGER.debug("received message: {}", message);

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

        if ("text/plain".equals(contentType) || "application/json".equals(contentType)) {
            //TODO: only interceptor plain text in the message body at the moment
            String messageString = new String(bytes, contentEncoding);
            LOGGER.debug("message string: " + messageString);

            // TODO: add specific implementation based on the routing key
            try {
                // simulate I/O latency in the processing of message
                // put a hard-delay less than the pre-configured hystrix timeout otherwise hystrix will break the circuit
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
            // acknowledge message is processed and can be removed from queue
            channel.basicAck(deliveryTag, false);
            LOGGER.debug("message acknowledged");

        } else {
            throw new RuntimeException("un-supported content type: " + contentType);
        }
    }
}
