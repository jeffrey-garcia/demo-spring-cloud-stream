package com.example.demo.bindings;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.annotation.Publisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@EnableBinding(Processor.class)
public class DemoPublisher {

    @Autowired
    Processor processor;

    @Publisher(channel = Source.OUTPUT)
    public void sendMessage() {
        Message message = message("testing 1");
        processor.output().send(message);
    }

    private static final <T> Message<T> message(T val) {
        return MessageBuilder.withPayload(val).build();
    }
}
