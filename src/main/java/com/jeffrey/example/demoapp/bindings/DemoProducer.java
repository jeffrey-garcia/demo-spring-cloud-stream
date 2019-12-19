package com.jeffrey.example.demoapp.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;

import java.io.IOException;

@EnableBinding(Source.class)
public class DemoProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoProducer.class);

    @Autowired
    Source source;

    @Publisher(channel = Source.OUTPUT)
    public boolean sendMessage(Message<?> message) throws IOException {
        boolean result = source.output().send(message);
        LOGGER.debug("send result: {}", result);
        return result;
    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void onError(ErrorMessage errorMessage) {
        LOGGER.debug("on error");
    }

    @ServiceActivator(inputChannel = "publisher-confirm")
    public void onPublisherConfirm(Message message) {
        LOGGER.debug("on publisher confirm");
    }

}
