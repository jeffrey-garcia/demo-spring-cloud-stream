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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Source;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({
        DemoMongoDbConfig.class,
        EventStoreRepository.class,
        EventStoreService.class,
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EventStoreServiceIT {

    @Value("${eventstore.producer.timeout.seconds:15}")
    long producerTimeoutInSec;

    @Value("${eventstore.retry.backoff.milliseconds:30000}")
    long retryBackoffTimeInMs;

    @Autowired
    EventStoreRepository eventStoreRepository;

    @Autowired
    EventStoreService eventStoreService;

    @Autowired
    Source source;

    @Before
    public void setUp() {
        eventStoreRepository.deleteAll();
    }

    @Test
    public void test() throws IOException {
        Message message = MessageBuilder.withPayload("testing message").build();
        DomainEvent domainEvent1 = eventStoreService.upsertEvent(message);
        DomainEvent domainEvent2 = eventStoreService.upsertEvent(message);
        Assert.assertEquals(2, eventStoreRepository.findAll().size());
    }

    @Test
    public void testFindAllPendingProducerAckEvents() throws IOException, InterruptedException {
        Message message = MessageBuilder.withPayload("testing message").build();
        eventStoreService.upsertEvent(message);
        List<DomainEvent> domainEventList = eventStoreService.findAllPendingProducerAckEvents();
        Assert.assertEquals(0, domainEventList.size());

        Thread.sleep(producerTimeoutInSec * 1000);
        domainEventList = eventStoreService.findAllPendingProducerAckEvents();
        Assert.assertEquals(1, domainEventList.size());

        domainEventList = eventStoreRepository.findAll();
        Assert.assertTrue(domainEventList.get(0).getWrittenOn().isBefore(LocalDateTime.now().minusSeconds(producerTimeoutInSec)));
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
    public void testConcurrentRetry() throws Throwable {
        final int MAX_MESSAGE = 2;
        for (int i=0; i<MAX_MESSAGE; i++) {
            eventStoreService.upsertEvent(MessageBuilder.withPayload("testing message " + i).build());
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
