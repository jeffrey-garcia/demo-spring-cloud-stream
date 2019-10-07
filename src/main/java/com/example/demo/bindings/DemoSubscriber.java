//package com.example.demo.bindings;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.cloud.stream.annotation.EnableBinding;
//import org.springframework.cloud.stream.annotation.StreamListener;
//import org.springframework.cloud.stream.messaging.Processor;
//import org.springframework.cloud.stream.messaging.Sink;
//
//@EnableBinding(Processor.class)
//public class DemoSubscriber {
//    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSubscriber.class);
//
//    @StreamListener(Sink.INPUT)
//    public void listen(String payload) {
//        LOGGER.debug("payload: {}", payload);
//    }
//
//}
