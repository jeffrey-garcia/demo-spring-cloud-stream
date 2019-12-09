package com.jeffrey.example.demoapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class EventStoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreConfig.class);

    @Value("${eventstore.retry.backoff.milliseconds:90000}") // retry backoff default to 90s if undefined
    long retryBackoffTimeInMs;

    @Value("${eventstore.timezone:#{null}}") // zoneIdString default null if undefined
    String zoneIdString;

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
    @Qualifier("eventStoreClock")
    public Clock eventStoreClock() {
        // timezone default to system if undefined
        Clock clock = zoneIdString != null ? Clock.system(ZoneId.of(zoneIdString)):Clock.systemDefaultZone();
        return clock;
    }

    @Bean
    @Qualifier("eventIdGenerator")
    public IdGenerator idGenerator() {
        return new AlternativeJdkIdGenerator();
    }

}
