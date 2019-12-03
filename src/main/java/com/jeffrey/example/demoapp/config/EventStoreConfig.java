package com.jeffrey.example.demoapp.config;

import com.jeffrey.example.demoapp.bindings.DemoProducer;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class EventStoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreConfig.class);

    @Value("${eventstore.retry.backoff.milliseconds:30000}")
    long retryBackoffTimeInMs;

    @Bean
    @Qualifier("eventStoreRetryTemplate")
    public RetryTemplate eventStoreRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffTimeInMs);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        return retryTemplate;
    }

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    MongoMappingContext mongoMappingContext;

    @Autowired
    @Qualifier("eventStoreRetryTemplate")
    RetryTemplate eventStoreRetryTemplate;

    @Autowired
    DemoProducer producer;

    @EventListener(ApplicationReadyEvent.class)
    public void postApplicationStartup() {
        // Although index creation via annotations comes in handy for many scenarios
        // consider taking over more control by setting up indices manually via IndexOperations.
        IndexOperations indexOps = mongoTemplate.indexOps(DomainEvent.class);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
        resolver.resolveIndexFor(DomainEvent.class).forEach(indexOps::ensureIndex);

        eventStoreRetryTemplate.execute(new RetryCallback<Void, RuntimeException>() {
            @Override
            public Void doWithRetry(RetryContext retryContext) {
                LOGGER.debug("retry couunt: {}", retryContext.getRetryCount());
                producer.retryProducing();
                return null;
            }
        });
    }
}
