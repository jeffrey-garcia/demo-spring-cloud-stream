package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.config.DemoMongoDbConfig;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Import({
        DemoMongoDbConfig.class,
        EventStoreRepository.class,
        EventStoreService.class,
})
@DataMongoTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class EventStoreServiceIT {

    @Value("${eventstore.retry.message.expired.seconds:15}")
    long messageExpiredTimeInSec;

    @Value("${eventstore.retry.backoff.milliseconds:30000}")
    long retryBackoffTimeInMs;

    @Autowired
    EventStoreRepository eventStoreRepository;

    @Autowired
    EventStoreService eventStoreService;

    @Before
    public void setUp() {
        eventStoreRepository.deleteAll();
    }

    @Test
    public void testCreateEvent() throws IOException {
        Message message = MessageBuilder.withPayload("testing message").build();
        eventStoreService.createEventFromMessage(message);
        eventStoreService.createEventFromMessage(message);
        Assert.assertEquals(2, eventStoreRepository.findAll().size());
    }

    @Test
    public void testRetry() throws Throwable {
        final int MAX_RETRY = 3;

        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(1);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_RETRY);
        retryTemplate.setRetryPolicy(retryPolicy);

        final AtomicInteger count = new AtomicInteger();

        RetryCallback retryCallback = (RetryCallback<Void, RuntimeException>) retryContext -> {
            count.incrementAndGet();
            throw new RuntimeException("initiate next retry with backoff period");
        };

        RetryService retryService = new RetryService(retryTemplate, retryCallback);
        try {
            retryService.execute();
        } catch (Exception e) { }

        Assert.assertEquals(MAX_RETRY, count.get());
    }

    @Test
    public void testConcurrentRetry_noProducerAck() throws Throwable {
        final int MAX_MESSAGE = 2;
        for (int i=0; i<MAX_MESSAGE; i++) {
            eventStoreService.createEventFromMessage(MessageBuilder.withPayload("testing message " + i).build());
        }
        Thread.sleep(retryBackoffTimeInMs);

        final int MAX_ATTEMPT = 3;

        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffTimeInMs);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_ATTEMPT);
        retryTemplate.setRetryPolicy(retryPolicy);

        final AtomicInteger count = new AtomicInteger();

        RetryCallback retryCallback = (RetryCallback<Void, RuntimeException>) retryContext -> {
            count.incrementAndGet();
            eventStoreService.retryOperation();
            throw new RuntimeException("initiate next retry with backoff period");
        };
        RetryService retryService = new RetryService(retryTemplate, retryCallback);

        final int MAX_THREAD = 4;
        final CountDownLatch lock = new CountDownLatch(MAX_THREAD);
        final Executor executor = Executors.newFixedThreadPool(MAX_THREAD);
        for (int i=0; i<MAX_THREAD; i++) {
            executor.execute(() -> {
                try {
                    retryService.execute();
                } catch (Exception e) { }
                lock.countDown();
            });
        }

        try {
            lock.await();
        } catch (InterruptedException e) { }

        Assert.assertEquals(MAX_ATTEMPT * MAX_THREAD, count.get());

        List<DomainEvent> domainEvents = eventStoreRepository.findAll();
        Assert.assertEquals(MAX_MESSAGE, domainEvents.size());
        domainEvents.forEach(domainEvent -> {
            Assert.assertEquals(MAX_ATTEMPT, domainEvent.getAttemptCount());
        });
    }

    @Test
    public void testTimeZone() {
        Assert.fail("not implemented");
    }

    @Test
    public void testDuplicatedEvent() {
        Assert.fail("not implemented");
    }

    @Test
    public void testMessageSerializing() {
        Assert.fail("not implemented");
    }

}
