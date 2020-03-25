package com.jeffrey.example.demoapp.controller;

import com.jeffrey.example.demoapp.bindings.DemoProducer;
import com.jeffrey.example.demoapp.config.DemoRabbitConfig;
import com.jeffrey.example.demoapp.model.DemoInsurancePolicy;
import com.jeffrey.example.demoapp.model.DemoMessageModel;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DemoController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoController.class);

    private static final Message<DemoMessageModel> message(String val) {
        DemoMessageModel demoMessageModel = new DemoMessageModel(new DemoInsurancePolicy(UUID.randomUUID().toString(), val));
        return MessageBuilder.withPayload(demoMessageModel).build();
    }

    @Autowired
    @Qualifier("rabbitTemplate")
    RabbitTemplate rabbitTemplate;


    @Autowired
    TopicExchange exchange;

    @GetMapping("/test0")
    public @ResponseBody ResponseEntity test0() {
        // handle the publish of message
        rabbitTemplate.convertAndSend(exchange.getName(), DemoRabbitConfig.ROUTING_KEY, "testing 0");
        return ResponseEntity.accepted().build();
    }

    @Autowired
    DemoProducer producer;

    @GetMapping("/test1")
    public @ResponseBody ResponseEntity test1() {
        try {
            boolean result = producer.sendMessage(message("testing 1"));
            if (result) {
                return ResponseEntity.status(HttpStatus.CREATED).body(HttpStatus.CREATED.toString());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/test2")
    public @ResponseBody ResponseEntity test2() {
        try {
            boolean result = producer.sendMessage2(message("testing 2"));
            if (result) {
                return ResponseEntity.status(HttpStatus.CREATED).body(HttpStatus.CREATED.toString());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

}
