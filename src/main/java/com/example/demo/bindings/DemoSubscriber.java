package com.example.demo.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.messaging.Message;

@EnableBinding(Sink.class)
public class DemoSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSubscriber.class);

    @StreamListener(Sink.INPUT)
    public void listen(Message<String> message){
        LOGGER.debug("message received from queue-1, payload: {}", message.getPayload());
    }

}
