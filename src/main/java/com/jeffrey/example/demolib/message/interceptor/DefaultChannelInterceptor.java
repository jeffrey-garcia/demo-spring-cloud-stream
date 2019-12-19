package com.jeffrey.example.demolib.message.interceptor;

import com.jeffrey.example.demolib.message.command.ChannelInterceptCommand;
import com.jeffrey.example.demolib.shutdown.service.GracefulShutdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

public class DefaultChannelInterceptor implements ChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChannelInterceptor.class);

    protected ChannelInterceptCommand<Message<?>> defaultCommand;
    protected ChannelInterceptCommand<Message<?>> command;

    protected GracefulShutdownService gracefulShutdownService;

    public DefaultChannelInterceptor(BeanFactory beanFactory) {
        try {
            GracefulShutdownService gracefulShutdownService = beanFactory.getBean(GracefulShutdownService.class);
            this.gracefulShutdownService = gracefulShutdownService;
        } catch (Exception e) {
            LOGGER.warn("graceful shutdown is not configured: {}", e.getMessage());
        }
    }

    public ChannelInterceptor configure(ChannelInterceptCommand<Message<?>> command) {
        this.command = command;
        return this;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        try {
            boolean shutdownInProgress = gracefulShutdownService!=null && gracefulShutdownService.isInvoked();
            if (shutdownInProgress) {
                if (this.command == null) {
                    if (defaultCommand != null)
                        return defaultCommand.invoke(message, messageChannel);
                    else
                        return message;
                }
                return command.invoke(message, messageChannel);
            } else {
                return message;
            }

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

}
