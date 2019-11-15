package com.example.demo.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.List;

public class RabbitBinderInterceptor implements ChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitBinderInterceptor.class);

    private final ChannelInterceptCommand<Message> defaultCommand = (message, messageChannel) -> {
        try {
            List xDeath = message.getHeaders().get("x-death", List.class);
            if (xDeath!=null && xDeath.size() > 0) {
                // if DLQ configured, divert the message to dead-letter-queue
                // once the dlq backoff time expired, the message is automatically put back to original queue
                throw new AmqpRejectAndDontRequeueException("intercept rabbit binder!");
            } else {
                // return null if no DLQ is configured
                // the state of the message remains as NACK until app restart
                // MessageDeliveryException will be thrown in the message container
                return null;
            }

        } catch (RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }


        /**
         * DLQ is configured and internal retry disabled, the message will be
         * routed to dead-letter-queue, when the backoff time expired the message
         * is put back to original queue
         */
        throw new AmqpRejectAndDontRequeueException("intercept rabbit binder!");
    };

    private final ChannelInterceptCommand<Message> command;

    public RabbitBinderInterceptor(ChannelInterceptCommand command) {
        this.command = command;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        try {
            if (this.command == null) {
                return defaultCommand.invoke(message, messageChannel);
            } else {
                return command.invoke(message, messageChannel);
            }
        } catch (Exception e) {
            throw new RuntimeException("error intercepting preSend", e);
        }
    }

}
