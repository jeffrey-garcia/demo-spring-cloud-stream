package com.jeffrey.example.demolib.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.List;

public class RabbitBinderInterceptor extends DefaultChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitBinderInterceptor.class);

    public RabbitBinderInterceptor() {
        defaultCommand = (message, messageChannel) -> {
            try {
                List xDeath = message.getHeaders().get("x-death", List.class);
                if (xDeath != null && xDeath.size() > 0) {
                    // if DLQ configured, divert the message to dead-letter-queue
                    // once the dlq backoff time expired, the message is automatically put back to original queue

                    // use reflection here to ensure package is lightweight and agnostic to actual binder implementation
                    // is the accountability of the app developer to wire Rabbit/AMQP specific dependencies in the app's project classpath
                    Class amqpRejectAndDontRequeueException = Class.forName("org.springframework.amqp.AmqpRejectAndDontRequeueException");
                    Constructor constructor = amqpRejectAndDontRequeueException.getConstructor(String.class);
                    RuntimeException exception = (RuntimeException) constructor.newInstance("intercept rabbit binder!");
                    throw exception;

                } else {
                    // return null if no DLQ is configured
                    // the state of the message remains as NACK until app restart
                    // MessageDeliveryException will be thrown in the message container
                    return null;
                }

            } catch (Exception e) {
                throw e;
            }
        };
    }

}
