//package com.example.demo.config;
//
//import brave.CurrentSpanCustomizer;
//import brave.SpanCustomizer;
//import brave.Tracer;
//import brave.Tracing;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import zipkin2.Span;
//import zipkin2.reporter.AsyncReporter;
//import zipkin2.reporter.okhttp3.OkHttpSender;
//
//@Configuration
//public class SleuthConfiguration {
//    private static final Logger LOGGER = LoggerFactory.getLogger(SleuthConfiguration.class);
//
//    @Bean
//    @Qualifier("sender")
//    OkHttpSender sender() {
//        return OkHttpSender.create("http://localhost:9411/api/v2/spans");
//    }
//
//    @Bean
//    @Qualifier("spanReporter")
//    AsyncReporter<Span> spanReporter(@Autowired OkHttpSender sender) {
//        // Configure a reporter, which controls how often spans are sent
//        return AsyncReporter.create(sender);
//    }
//
//    @Bean
//    @Qualifier("tracing")
//    Tracing tracing(@Autowired AsyncReporter<Span> spanReporter) {
//        // Create a tracing component with the service name you want to see in Zipkin.
//        return Tracing.newBuilder()
//                .localServiceName("demo")
//                .spanReporter(spanReporter)
//                .build();
//    }
//
//    @Bean
//    @Qualifier("tracer")
//    Tracer tracer(@Autowired Tracing tracing) {
//        // Tracing exposes objects you might need, most importantly the tracer
//        return tracing.tracer();
//    }
//
//    @Bean
//    @Qualifier("currentSpanCustomizer")
//    SpanCustomizer currentSpanCustomizer(@Autowired Tracing tracing) {
//        // Some DI configuration wires up the current span customizer
//        return CurrentSpanCustomizer.create(tracing);
//    }
//
//}