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
        DomainEvent event = eventStoreService.upsertEvent(message);

        MessageHeaderAccessor accessor = MessageHeaderAccessor.getMutableAccessor(message);
        accessor.setHeader("eventId", event.getId());
        MessageHeaders messageHeaders = accessor.getMessageHeaders();
        message = MessageBuilder.fromMessage(message).copyHeaders(messageHeaders).build();

        boolean result = source.output().send(message);
        LOGGER.debug("send result: {}", result);
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

    // TODO: this should be in the infrastructure bean
//    public void retryProducing() {
//        // fetch all event that has not been sent to broker
//        List<DomainEvent> pendingEvents = eventStoreService.findAllPendingProducerAckEvents();
//        LOGGER.debug("number of pending events: {}", pendingEvents.size());
//
//        for (DomainEvent pendingEvent:pendingEvents) {
//            String eventId = pendingEvent.getId();
//            LOGGER.debug("retrying event id: {}", eventId);
//
//            try {
//                Map headers = ObjectMapperFactory.getMapper().fromJson(pendingEvent.getHeader(), Map.class);
//                headers.put("eventId", eventId);
//                String payload = ObjectMapperFactory.getMapper().fromJson(pendingEvent.getPayload(), String.class);
//
//                Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
//                LOGGER.debug("retry producing message: {}", message);
//
//                sendMessage(message);
//
//            } catch (Exception e) { }
//        }
//    }
}
