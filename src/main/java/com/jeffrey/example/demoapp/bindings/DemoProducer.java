package com.jeffrey.example.demoapp.bindings;

import com.jeffrey.example.demoapp.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;

import java.io.IOException;


@EnableBinding(Source.class)
public class DemoProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoProducer.class);

    @Autowired
    Source source;

    @Autowired
    EventStoreService eventStoreService;

    @Publisher(channel = Source.OUTPUT)
    public void sendMessage(Message<?> message) throws IOException {
        message = eventStoreService.createEventFromMessage(message, Source.OUTPUT);
        boolean result = source.output().send(message);
        LOGGER.debug("send result: {}", result);
    }

    // TODO: move this to the infrastructure bean
    @ServiceActivator(inputChannel = "errorChannel")
    public void onError(ErrorMessage errorMessage) {
        /**
         * Global error messages interceptor
         *
         * The error channel gets an ErrorMessage which has a Throwable payload.
         * Usually the Throwable is a message handling exception with the original
         * message in the failedMessage property and the exception in the cause.
         */
        MessagingException exception = (MessagingException) errorMessage.getPayload();

        // capture any publisher error
        if (exception instanceof ReturnedAmqpMessageException) {
            LOGGER.debug("error sending message to broker");
            ReturnedAmqpMessageException amqpMessageException = (ReturnedAmqpMessageException) exception;
            // producer's message not be accepted by RabbitMQ
            // the message is returned with a negative ack
            String errorReason = amqpMessageException.getReplyText();
            int errorCode = amqpMessageException.getReplyCode();

            org.springframework.amqp.core.Message amqpMessage = amqpMessageException.getAmqpMessage();
            String eventId = (String) amqpMessage.getMessageProperties().getHeaders().get("eventId");
            eventStoreService.updateEventAsReturned(eventId);
            LOGGER.debug("error reason: {}, error code: {}", errorReason, errorCode);
            return;
        }

        if (exception instanceof NackedAmqpMessageException) {
            LOGGER.debug("error sending message to broker");
            NackedAmqpMessageException nackedAmqpMessageException = (NackedAmqpMessageException) exception;
            String errorReason = nackedAmqpMessageException.getNackReason();

            String eventId = nackedAmqpMessageException.getFailedMessage().getHeaders().get("eventId", String.class);
            eventStoreService.updateEventAsReturned(eventId);
            LOGGER.debug("error reason: {}", errorReason);
            return;
        }

        if (exception instanceof MessageDeliveryException) {
            LOGGER.debug("error delivering message to consumer");
            MessageDeliveryException deliveryException = (MessageDeliveryException) exception;
            String errorReason = deliveryException.getMessage();
            LOGGER.debug("error reason: {}", errorReason);
            return;
        }
    }

    // TODO: move this to the infrastructure bean
    @ServiceActivator(inputChannel = "publisher-confirm")
    public void onPublisherConfirm(Message message) {
        /**
         * Global publisher confirm channel interceptor
         */
        Boolean publisherConfirm = message.getHeaders().get("amqp_publishConfirm", Boolean.class);
        if (publisherConfirm != null && publisherConfirm) {
            // TODO: returned message would also produce a positive ack
            eventStoreService.updateEventAsProduced(message.getHeaders().get("eventId", String.class));
            LOGGER.debug("message successfully published: {}", message.getPayload());
        }
    }

}
