package com.jeffrey.example.demolib.eventstore.config;

import com.jeffrey.example.demolib.eventstore.aop.EventStoreAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

import java.time.Clock;
import java.time.ZoneId;

@EnableAspectJAutoProxy
@Configuration
public class EventStoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreConfig.class);

    @Value("${com.jeffrey.example.eventstore.retry.backoff.milliseconds:90000}") // retry backoff default to 90s if undefined
    long retryBackoffTimeInMs;

    @Value("${com.jeffrey.example.eventstore.timezone:#{null}}") // zoneIdString default null if undefined
    String zoneIdString;

    @Bean("eventStoreRetryTemplate")
    public RetryTemplate eventStoreRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffTimeInMs);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        return retryTemplate;
    }

    @Bean("eventStoreClock")
    public Clock eventStoreClock() {
        // timezone default to system if undefined
        Clock clock = zoneIdString != null ? Clock.system(ZoneId.of(zoneIdString)):Clock.systemDefaultZone();
        return clock;
    }

    @Bean("eventIdGenerator")
    public IdGenerator idGenerator() {
        /**
         * An IdGenerator that uses SecureRandom for the initial seed and Random thereafter,
         * instead of calling UUID.randomUUID() every time as JdkIdGenerator does.
         * This provides a better balance between securely random ids and performance.
         */
        return new AlternativeJdkIdGenerator();
    }

    @Bean("eventStoreAspect")
    public EventStoreAspect eventStoreAspect() {
        return new EventStoreAspect();
    }

}
