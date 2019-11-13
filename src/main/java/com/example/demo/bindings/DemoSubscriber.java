package com.example.demo.bindings;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.ErrorMessage;

import java.io.IOException;

@EnableBinding(Sink.class)
public class DemoSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSubscriber.class);

    @StreamListener(Sink.INPUT)
    public void listen(
            @Payload String messageString,
            @Header(AmqpHeaders.CHANNEL) Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag) throws IOException
    {
        LOGGER.debug("message received from queue-1, payload: {}", messageString);

        try {
            // simulate I/O latency in the processing of message
            // TODO: put a hard-delay less than the pre-configured hystrix timeout otherwise hystrix will break the circuit
            Thread.sleep(1000);

            // if the system crashed before the positive acknowledge,
            // the message will be re-queued and won't be lost

            // positive acknowledge to instruct RabbitMQ to record a message as delivered and can be discarded.
            channel.basicAck(deliveryTag, false);
            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);

        } catch (InterruptedException e) {
            // reject the message and re-queue it, if a DLQ is configured it will be routed to the DLQ
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void onError(ErrorMessage message) {
        /**
         * Intercept messages sent to the errorChannel
         */
        // capture any delivery error
        LOGGER.debug("onError: headers:{}, payload:{}", message.getHeaders(), message.getPayload().getMessage());
    }

}
