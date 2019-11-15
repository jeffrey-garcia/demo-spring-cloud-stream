package com.example.demo.bindings;

import com.example.demo.channel.ErrorSink;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.ErrorMessage;

import java.util.Map;

@EnableBinding({Sink.class, ErrorSink.class})
public class DemoSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSubscriber.class);

    /**
     * If DLQ is not configured AND acknowledgement mode is AUTO
     */
//    @StreamListener(Sink.INPUT)
//    public void listen(
//            @Payload String messageString
//    ) throws Exception {
//        LOGGER.debug("message received from: {}, payload: {}", Sink.INPUT, messageString);
//
//        try {
//            // simulate I/O latency in the processing of message
//            Thread.sleep(1000);
//
//            throw new Exception("dummy exception!");
//
//        } catch (Exception e) {
//            /**
//             * when exception is thrown the message is rejected
//             * - if maxAttempt > 1, the failed message will be requeue and retry until exhausted,
//             *   when retry count exhausted the message will be discarded
//             * - if maxAttempt = 1 and requeueRejected = true, the failed message will be requeue
//             *   for infinite retry
//             */
//            throw e;
//        }
//    }

    /**
     * If DLQ is not configured AND acknowledgement mode is MANUAL
     */
//    @StreamListener(Sink.INPUT)
//    public void listen(
//            @Payload String messageString,
//            @Header(AmqpHeaders.CHANNEL) Channel channel,
//            @Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag
//    ) throws Exception
//    {
//        LOGGER.debug("message received from: {}, payload: {}", Sink.INPUT, messageString);
//
//        try {
//            // simulate I/O latency in the processing of message
//            Thread.sleep(1000);
//
//            // if the system crashed before the positive acknowledge,
//            // the message will be re-queued and won't be lost
//
//            throw new Exception("dummy exception!");
//
//            // positive acknowledge to instruct RabbitMQ to record a message as delivered and can be discarded.
////            channel.basicAck(deliveryTag, false);
////            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
//
//        } catch (Exception e) {
//            /**
//             * purposely reject the message and don't re-queue it
//             * - if a DLQ is configured the rejected message will be routed to the DLQ
//             */
////            channel.basicNack(deliveryTag, false, false);
//
//            /**
//             * purposely reject the message and re-queue it
//             * - if the problem is permanent (i.e. application bug causing a failure to consume the message), it will cause
//             * infinite retry regardless of the max-attempt count
//             * - consider to apply it for transient problem only
//             */
////            channel.basicNack(deliveryTag, false, true);
//
//            throw e;
//        }
//    }

    /**
     * If DLQ is configured AND acknowledgement mode is AUTO
     */
    @StreamListener(Sink.INPUT)
    public void listen(
            @Payload String messageString,
            @Header(name = "x-death", required = false) Map<?,?> death
    ) throws Exception {
        LOGGER.debug("message received from: {}, payload: {}", Sink.INPUT, messageString);

        try {
            // simulate I/O latency in the processing of message
            Thread.sleep(1000);

            // if the system crashed before the positive acknowledge,
            // the message will be re-queued and won't be lost

            throw new Exception("dummy exception!");

        } catch (Exception e) {
            /**
             * force a message to be dead-lettered
             * requeueRejected should be set to false, otherwise throwing other exception will cause
             * the message to be immediately re-queued (infinite retry instead of routed to DLQ)
             */
            throw e;
        }
    }


    /**
     * If DLQ is configured AND acknowledgement mode is MANUAL
     */
//    @StreamListener(Sink.INPUT)
//    public void listen(
//            @Payload String messageString,
//            @Header(name = "x-death", required = false) Map<?,?> death,
//            @Header(AmqpHeaders.CHANNEL) Channel channel,
//            @Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag
//    ) throws Exception {
//        LOGGER.debug("message received from: {}, payload: {}", Sink.INPUT, messageString);
//
//        try {
//            // simulate I/O latency in the processing of message
//            Thread.sleep(1000);
//
//            // if the system crashed before the positive acknowledge,
//            // the message will be re-queued and won't be lost
//
//            throw new Exception("dummy exception!");
//
//            // positive acknowledge to instruct RabbitMQ to record a message as delivered and can be discarded.
////            channel.basicAck(deliveryTag, false);
////            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
//
//        } catch (Exception e) {
//            /**
//             * purposely reject the message and don't re-queue it
//             * - if a DLQ is configured the rejected message will be routed to the DLQ
//             */
//            channel.basicNack(deliveryTag, false, false);
//
//            /**
//             * force a message to be dead-lettered
//             * - throwing other exception will cause the message to be immediately re-queued
//             *  (infinite retry without routed to DLQ)
//             * - only work when consumer acknowledgement is not required, i.e. AUTO, if the
//             * acknowledgement mode is manual, the message will stay in un-ack state and the
//             * consumer will be blocked
//             */
//            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
////            throw e;
//        }
//    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void onError(ErrorMessage errorMessage) {
        /**
         * Intercept messages sent to the errorChannel
         */
        // capture any delivery error
        LOGGER.debug("onError: headers:{}, payload:{}", errorMessage.getHeaders(), errorMessage.getPayload().getMessage());
    }

//    @ServiceActivator(inputChannel = "errorChannel", outputChannel = "error")
//    public ErrorMessage onError(ErrorMessage errorMessage) {
//        /**
//         * Intercept messages sent to the errorChannel
//         */
//        // capture any delivery error
//        LOGGER.debug("onError: headers:{}, payload:{}", errorMessage.getHeaders(), errorMessage.getPayload().getMessage());
//
//        // consume these exceptions and handle it with custom spring integration flow
//        return errorMessage;
//    }

//    @StreamListener("errorChannel")
//    public void error(Message<String> message) {
//        LOGGER.debug(" Handling ERROR: {}", message);
//    }

}
