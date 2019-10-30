package com.example.demo.bindings;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

@EnableBinding(Sink.class)
public class DemoSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSubscriber.class);

    @StreamListener(Sink.INPUT)
    public void listen(
            @Payload String messageString,
            @Header(AmqpHeaders.CHANNEL) Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag) throws Exception
    {
        LOGGER.debug("message received from queue-1, payload: {}", messageString);

        try {
            // simulate I/O latency in the processing of message
            // TODO: put a hard-delay less than the pre-configured hystrix timeout otherwise hystrix will break the circuit
            Thread.sleep(4000);
        } catch (InterruptedException e) {
        }

        LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
        // acknowledge message is processed and can be removed from queue
        channel.basicAck(deliveryTag, false);
    }

}
