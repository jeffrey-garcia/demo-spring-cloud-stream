package com.example.demo.config;


import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
public class RabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitConfig.class);

//    @Autowired
//    @Qualifier("tracing")
//    Tracing tracing;
//
//    @Autowired
//    @Qualifier("tracer")
//    Tracer tracer;

    @RabbitListener(queues = "demo-exchange.demo-queue-2")
    public void onMessage(Message message, Channel channel) {
//        Span span = tracer.currentSpan().name("demo/onMessage").kind(SERVER);
//        span.remoteServiceName("rabbitmq");
//        span.remoteIpAndPort("127.0.0.1", 5672);

        // handle the consuming of message
        LOGGER.debug("received message: {}", message);

//        Map<String, Object> msgHeaders = message.getHeaders();
//        Object headersObj = msgHeaders.get("zipkin.brave.tracing.headers");
//        Map<String, String> strHeaders = headersObj == null? null:(HashMap<String, String>)headersObj;
//        TraceContextOrSamplingFlags result = tracing.propagation().extractor(Map<String, String>::get).extract(strHeaders);
//
//        if(result.context() != null){
//            Span currentSpan = tracer.joinSpan(result.context());
//            LOGGER.debug("trace id: {}", currentSpan.context().traceIdString());
//            LOGGER.debug("span id: {}", currentSpan.context().spanIdString());
//
//            try (Tracer.SpanInScope spanInScope = tracer.withSpanInScope(currentSpan)) {
//                currentSpan.name("onMessage").kind(Span.Kind.SERVER).remoteServiceName("demo-service").start();
//
//                // handle the consuming of message
//                LOGGER.debug("received message: {}", message);
//
//            } catch (Exception | Error e) {
//                // Unless you handle exceptions, you might not know the operation failed!
//                currentSpan.error(e);
//                throw e;
//
//            } finally {
//                // note the scope is independent of the span. Always finish a span.
//                currentSpan.finish();
//            }
//        } else {
//            // handle the consuming of message
//            LOGGER.debug("received message: {}", message);
//        }

//        Span currentSpan = tracer.currentSpan();
//        try (Tracer.SpanInScope spanInScope = tracer.withSpanInScope(tracer.currentSpan())) {
//            currentSpan.name("onMessage").kind(Span.Kind.CONSUMER).remoteServiceName("demo");
//
//            // handle the consuming of message
//            LOGGER.debug("received message: {}", message);
//
//        } catch (Exception | Error e) {
//            // Unless you handle exceptions, you might not know the operation failed!
//            currentSpan.error(e);
//            throw e;
//
//        } finally {
//            // note the scope is independent of the span. Always finish a span.
//            currentSpan.flush();
//        }
    }

    @Bean
    Queue queue() {
        // durable MUST be true otherwise the queue (and everything in it) will be deleted when message broker restart
        return new Queue("demo-exchange.demo-queue-2");
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange("demo-exchange");
    }

    @Bean
    Binding bindingTestEvent1(Queue queue, TopicExchange exchange) {
        // bind this queue behind the exchange topic and configure the specific routing key
        return BindingBuilder.bind(queue).to(exchange).with("test.event.2");
    }


}
