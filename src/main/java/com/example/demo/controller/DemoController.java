package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class DemoController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoController.class);

//    @Autowired
//    DemoPublisher publisher;
//
//    @GetMapping("/test1")
//    public @ResponseBody ResponseEntity test1() {
//        publisher.sendMessage();
//        return ResponseEntity.accepted().build();
//    }

    @Autowired
    RabbitTemplate rabbitTemplate;

//    @Autowired
//    @Qualifier("tracing")
//    Tracing tracing;
//
//    @Autowired
//    @Qualifier("tracer")
//    Tracer tracer;
//
//    // user code can then inject this without a chance of it being null.
//    @Autowired
//    @Qualifier("currentSpanCustomizer")
//    SpanCustomizer span;

    @GetMapping("/test2")
    public @ResponseBody ResponseEntity test2() {

//        Span span = tracer.currentSpan().name("demo/test2").kind(CLIENT);
//        span.remoteServiceName("rabbitmq");
//        span.remoteIpAndPort("127.0.0.1", 5672);

//        Map<String, String> strHeaders = new HashMap<>();
//        tracing.propagation().injector(Map<String, String>::put).inject(span.context(), strHeaders);
//
//        Message message = MessageBuilder
//                            .withPayload("testing 2")
//                            .setHeader(
//                                    "zipkin.brave.tracing.headers",
//                                    strHeaders
//                            ).build();
//
//        // when the request is scheduled, start the span
//        span.start();

        // handle the publish of message
        rabbitTemplate.convertAndSend("demo-exchange","test.event.2", "testing 2");

        // when the response is complete, finish the span
//        span.flush();

//        Span currentSpan = tracer.currentSpan();
//        if (currentSpan==null) currentSpan = tracer.newTrace();
//        try (Tracer.SpanInScope spanInScope = tracer.withSpanInScope(tracer.currentSpan())) {
//            currentSpan.name("test2").kind(Span.Kind.CLIENT).remoteServiceName("demo-service").start();
//
//            Map<String, String> strHeaders = new HashMap<String, String>();
//            tracing.propagation().injector(Map<String, String>::put).inject(currentSpan.context(), strHeaders);
//
//            Message message = MessageBuilder
//                    .withPayload("testing 2")
//                    .setHeader(
//                            "zipkin.brave.tracing.headers",
//                            strHeaders
//                    ).build();
//
//            // handle the publish of message
//            rabbitTemplate.convertAndSend("demo-exchange","test.event.2", message);
//
//        } catch (Exception | Error e) {
//            // Unless you handle exceptions, you might not know the operation failed!
//            currentSpan.error(e);
//            throw e;
//
//        } finally {
//            // note the scope is independent of the span. Always finish a span.
//            currentSpan.finish();
//        }

        return ResponseEntity.accepted().build();
    }

}
