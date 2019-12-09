package com.jeffrey.example.demoapp.config;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.service.EventStoreService;
import com.jeffrey.example.demoapp.service.RetryService;
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
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class EventStoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreConfig.class);

    @Value("${eventstore.retry.backoff.milliseconds:90000}") // retry backoff default to 90s if undefined
    long retryBackoffTimeInMs;

    @Value("${eventstore.retry.autoStart:true}")
    boolean autoStart;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    MongoMappingContext mongoMappingContext;

    @Autowired
    EventStoreService eventStoreService;

    @Autowired
    RetryService eventStoreRetryService;

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

    @Bean
    @Qualifier("eventStoreRetryCallback")
    public RetryCallback<Void, RuntimeException> retryCallback() {
        return retryContext -> {
            LOGGER.debug("retry count: {}", retryContext.getRetryCount());
            eventStoreService.fetchEventAndResend();
            // throw RuntimeException to initiate next retry
            throw new RuntimeException("initiate next retry");
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void postApplicationStartup() {
        // Although index creation via annotations comes in handy for many scenarios
        // consider taking over more control by setting up indices manually via IndexOperations.
        IndexOperations indexOps = mongoTemplate.indexOps(DomainEvent.class);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
        resolver.resolveIndexFor(DomainEvent.class).forEach(indexOps::ensureIndex);

        if (autoStart) {
            eventStoreRetryService.execute();
        }
    }

}
