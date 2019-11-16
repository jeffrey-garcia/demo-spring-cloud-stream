package com.jeffrey.example.demolib.interceptor;

import com.jeffrey.example.demolib.command.ChannelInterceptCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

public class DefaultChannelInterceptor implements ChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChannelInterceptor.class);

    protected ChannelInterceptCommand<Message<?>> defaultCommand;
    protected ChannelInterceptCommand<Message<?>> command;

    public ChannelInterceptor configure(ChannelInterceptCommand<Message<?>> command) {
        this.command = command;
        return this;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        try {
            if (this.command == null) {
                if (defaultCommand != null)
                    return defaultCommand.invoke(message, messageChannel);
                else
                    return message;
            }
            return command.invoke(message, messageChannel);

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

}
