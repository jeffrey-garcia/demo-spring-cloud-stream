package com.jeffrey.example.demoapp.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.annotation.Publisher;
import org.springframework.messaging.Message;
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
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private static final <T> Message<T> message(T val) {
        return MessageBuilder.withPayload(val).build();
    }
}
