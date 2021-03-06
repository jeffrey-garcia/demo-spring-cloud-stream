package com.jeffrey.example.demoapp.bindings;

import com.jeffrey.example.demoapp.config.DemoChannelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.integration.annotation.Publisher;
import org.springframework.messaging.Message;

import java.io.IOException;

@EnableBinding({DemoChannelConfig.class})
public class DemoProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoProducer.class);

    @Autowired
    DemoChannelConfig demoChannelConfig;

    @Publisher(channel = DemoChannelConfig.OUTPUT1)
    public boolean sendMessage(Message<?> message) throws IOException {
        boolean result = demoChannelConfig.output1().send(message);
        LOGGER.debug("send result: {}", result);
        return result;
    }

    @Publisher(channel = DemoChannelConfig.OUTPUT2)
    public boolean sendMessage2(Message<?> message) throws IOException {
        boolean result = demoChannelConfig.output2().send(message);
        LOGGER.debug("send result: {}", result);
        return result;
    }

}
