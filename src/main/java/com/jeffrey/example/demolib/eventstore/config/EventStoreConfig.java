package com.jeffrey.example.demolib.eventstore.config;

import com.jeffrey.example.demolib.eventstore.aop.EventStoreAspect;
import com.jeffrey.example.demolib.eventstore.service.EventStoreService;
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
import java.util.UUID;

/**
 * Configuration class which hook up event store components with externalized configuration
 *
 * @see EventStoreAspect
 * @author Jeffrey Garcia Wong
 */
@EnableAspectJAutoProxy
@Configuration
public class EventStoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreConfig.class);

    @Value("${com.jeffrey.example.eventstore.retry.backoff.milliseconds:90000}") // retry backoff default to 90s if undefined
    long retryBackoffTimeInMs;

    @Value("${com.jeffrey.example.eventstore.timezone:#{null}}") // zoneIdString default null if undefined
    String zoneIdString;

    /**
     * Obtain a {@link RetryTemplate} for executing the retry operation of event store.
     *
     * <p>
     * The retry operation uses a {@link FixedBackOffPolicy} so the interval between retries
     * are in fixed time.
     * The next retry should only be scheduled when the previous attempt finished, this avoid
     * scenario where the current retry still processing the messages while the next retry is
     * triggered.
     * See also {@link EventStoreService#postApplicationStartup()}
     * </p>
     * @return {@link RetryTemplate}
     */
    @Bean("eventStoreRetryTemplate")
    public RetryTemplate eventStoreRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffTimeInMs);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        return retryTemplate;
    }

    /**
     * Create a global system {@link Clock} configured with a specified timezone
     * (Zone ID String).
     *
     * <p>
     * Default to system timezone if undefined (not recommended if the system is
     * distributed to run in multiple geographical locations which may observe
     * difference in timezone.
     * </p>
     *
     * @return a {@link Clock}
     */
    @Bean("eventStoreClock")
    public Clock eventStoreClock() {
        Clock clock = zoneIdString != null ? Clock.system(ZoneId.of(zoneIdString)):Clock.systemDefaultZone();
        return clock;
    }

    /**
     * Define an {@link IdGenerator} that uses {@link java.security.SecureRandom} for the initial
     * seed and Random thereafter, instead of calling {@link UUID#randomUUID()} every time
     * as {@link org.springframework.util.JdkIdGenerator} does.
     *
     * <p>This provides a better balance between securely random ids and performance.</p>
     *
     * @return an {@link AlternativeJdkIdGenerator}
     */
    @Bean("eventIdGenerator")
    public IdGenerator idGenerator() {
        return new AlternativeJdkIdGenerator();
    }

    /**
     * Create an {@link EventStoreAspect} which is used for intercepting the global error channel
     * and publisher-confirm channel.
     *
     * @return {@link EventStoreAspect}
     */
    @Bean("eventStoreAspect")
    public EventStoreAspect eventStoreAspect() {
        return new EventStoreAspect();
    }

}
