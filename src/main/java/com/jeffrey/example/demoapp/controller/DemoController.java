package com.jeffrey.example.demoapp.controller;

import com.jeffrey.example.demoapp.bindings.DemoProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoController.class);

    @Autowired
    DemoProducer producer;

    @GetMapping("/test1")
    public @ResponseBody ResponseEntity test1() {
        try {
            producer.sendMessage(message("testing 1"));
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Autowired
    @Qualifier("rabbitTemplate")
    RabbitTemplate rabbitTemplate;

    @GetMapping("/test2")
    public @ResponseBody ResponseEntity test2() {
        // handle the publish of message
        rabbitTemplate.convertAndSend("demoapp-exchange","test.event.2", "testing 2");
        return ResponseEntity.accepted().build();
    }

    private static final <T> org.springframework.messaging.Message<T> message(T val) {
        return MessageBuilder.withPayload(val).build();
    }
}
