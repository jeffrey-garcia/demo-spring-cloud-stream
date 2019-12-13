package com.jeffrey.example.demoapp.bindings;

import com.jeffrey.example.demolib.eventstore.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

import java.util.Map;

@EnableBinding({Sink.class})
public class DemoConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoConsumer.class);

    @Autowired
    EventStoreService eventStoreService;

//    /**
//     * If DLQ is not configured AND acknowledgement mode is AUTO
//     */
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
//            // simulate any un-expected exception thrown in the business logic
//            //throw new Exception("dummy exception!");
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

//    /**
//     * If DLQ is not configured AND acknowledgement mode is MANUAL
//     */
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
//            // simulate any un-expected exception thrown in the business logic
//            //throw new Exception("dummy exception!");
//
//            // positive acknowledge to instruct RabbitMQ to record a message as delivered and can be discarded.
//            channel.basicAck(deliveryTag, false);
//            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
//
//        } catch (Exception e) {
//            /**
//             * purposely reject the message and don't re-queue it
//             * - if a DLQ is configured the rejected message will be routed to the DLQ
//             */
//            channel.basicNack(deliveryTag, false, false);
//
//            /**
//             * purposely reject the message and re-queue it
//             * - if the problem is permanent (i.e. application bug causing a failure to consume the message), it will cause
//             * infinite retry regardless of the max-attempt count
//             * - consider to apply it for transient problem only
//             */
//            channel.basicNack(deliveryTag, false, true);
//            throw e;
//        }
//    }

    /**
     * If DLQ is configured AND acknowledgement mode is AUTO
     */
    @StreamListener(Sink.INPUT)
    public void listen(
            @Payload String messageString,
            @Header(name = "x-death", required = false) Map<?,?> death,
            @Headers MessageHeaders headers
    ) throws Exception {
        LOGGER.debug("message received from: {}, payload: {}", Sink.INPUT, messageString);

        try {
            // simulate I/O latency in the processing of message
            Thread.sleep(1000);

            // if the system crashed before the positive acknowledge,
            // the message will be re-queued and won't be lost

            // simulate any un-expected exception thrown in the business logic
            // throw new Exception("dummy exception!");

            String eventId = headers.get("eventId", String.class);
            LOGGER.debug("message consumed, eventId: {}", eventId);
            eventStoreService.updateEventAsConsumed(eventId);

        } catch (Exception e) {
            /**
             * force a message to be dead-lettered
             * requeueRejected should be set to false, otherwise throwing other exception will cause
             * the message to be immediately re-queued (infinite retry instead of routed to DLQ)
             */
            throw e;
        }
    }


//    /**
//     * If DLQ is configured AND acknowledgement mode is MANUAL
//     */
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
//            // simulate any un-expected exception thrown in the business logic
//            // throw new Exception("dummy exception!");
//
//            // positive acknowledge to instruct RabbitMQ to record a message as delivered and can be discarded.
//            channel.basicAck(deliveryTag, false);
//            LOGGER.debug("finish processing message tag: {}, proceed to acknowledge", deliveryTag);
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
//             *  (infinite retry without routed to DLQ) if requeueRejected is false
//             * - only work when consumer acknowledgement is not required, i.e. AUTO, if the
//             * acknowledgement mode is manual, the message will stay in un-ack state and the
//             * consumer will be blocked
//             */
//            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
//        }
//    }

//    @ServiceActivator(inputChannel = "errorChannel")
//    public void onError(ErrorMessage errorMessage) {
//        /**
//         * Global error messages interceptor
//         */
//        MessagingException exception = (MessagingException) errorMessage.getPayload();
//        Message message = exception.getFailedMessage();
//        LOGGER.debug("original message: {}", new String((byte[])message.getPayload()));
//
//        if (exception instanceof MessageDeliveryException) {
//            MessageDeliveryException deliveryException = (MessageDeliveryException) exception;
//            String errorReason = deliveryException.getMessage();
//            LOGGER.debug("error reason: {}", errorReason);
//        }
//    }

}
