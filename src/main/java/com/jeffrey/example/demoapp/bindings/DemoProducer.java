package com.jeffrey.example.demoapp.bindings;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;


@EnableBinding(Source.class)
public class DemoProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoProducer.class);

    @Autowired
    Source source;

    @Autowired
    EventStoreService eventStoreService;

    @Publisher(channel = Source.OUTPUT)
    public void sendMessage() throws Exception {
        Message message = message("testing 1");

        try {
            DomainEvent event = eventStoreService.createEvent(message);

            MessageHeaderAccessor accessor = MessageHeaderAccessor.getMutableAccessor(message);
            accessor.setHeaderIfAbsent("eventId", event.getId());
            MessageHeaders messageHeaders = accessor.getMessageHeaders();

            Message _message = MessageBuilder.fromMessage(message).copyHeaders(messageHeaders).build();

            boolean result = source.output().send(_message);
            LOGGER.debug("send result: {}", result);
        } catch (Exception e) {
            // can only handle typical network exception
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private static final <T> Message<T> message(T val) {
        return MessageBuilder.withPayload(val).build();
    }

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

        // retrieve the original message for subsequent retry/error handling
        Message message = exception.getFailedMessage();
        LOGGER.debug("original message: {}", new String((byte[])message.getPayload()));

        // capture any publisher error
        if (exception instanceof ReturnedAmqpMessageException) {
            ReturnedAmqpMessageException amqpMessageException = (ReturnedAmqpMessageException) exception;
            // producer's message not be accepted by RabbitMQ
            // the message is returned with a negative ack
            String errorReason = amqpMessageException.getReplyText();
            int errorCode = amqpMessageException.getReplyCode();
            LOGGER.debug("error reason: {}, error code: {}", errorReason, errorCode);
        }
    }

    @ServiceActivator(inputChannel = "publisher-confirm")
    public void onPublisherConfirm(Message message) {
        /**
         * Global publisher confirm channel interceptor
         */
        Boolean publisherConfirm = message.getHeaders().get("amqp_publishConfirm", Boolean.class);
        if (publisherConfirm != null && publisherConfirm) {
            eventStoreService.updateEventAsProduced(message.getHeaders().get("eventId", String.class));
        }

        LOGGER.debug("{}", message.getPayload());
    }
}
