package com.jeffrey.example.demoapp.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

@EnableBinding(Source.class)
public class DemoPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoPublisher.class);

    @Autowired
    Source source;

    @Publisher(channel = Source.OUTPUT)
    public void sendMessage() throws Exception {
        Message message = message("testing 1");

        try {
            boolean result = source.output().send(message);
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

}
