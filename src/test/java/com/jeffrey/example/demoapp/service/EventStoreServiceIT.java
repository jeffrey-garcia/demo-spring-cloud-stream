package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.config.MongoDbConfig;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.model.DemoInsurancePolicy;
import com.jeffrey.example.demoapp.model.DemoMessageModel;
import com.jeffrey.example.demoapp.repository.EventStoreDao;
import com.jeffrey.example.demoapp.repository.MongoEventStoreDao;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Import({
        MongoDbConfig.class,
        MongoEventStoreDao.class,
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
    EventStoreDao eventStoreDao;

    @Autowired
    EventStoreService eventStoreService;

    @Before
    public void setUp() {
        eventStoreDao.deleteAll();
    }

    @Test
    public void testCreateEvent() throws IOException {
        Message message = MessageBuilder.withPayload("testing message").build();
        eventStoreService.createEventFromMessage(message);
        eventStoreService.createEventFromMessage(message);
        Assert.assertEquals(2, eventStoreDao.findAll().size());
    }

    @Test
    public void testFetchEventAndResend() {
        eventStoreService.fetchEventAndResend();
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

        final int INITIAL_ATTEMPT = 1;
        final int MAX_RETRY_ATTEMPT = 3;

        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffTimeInMs);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_RETRY_ATTEMPT);
        retryTemplate.setRetryPolicy(retryPolicy);

        final AtomicInteger count = new AtomicInteger();

        RetryCallback retryCallback = (RetryCallback<Void, RuntimeException>) retryContext -> {
            count.incrementAndGet();
            eventStoreService.fetchEventAndResend();
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

        Assert.assertEquals(MAX_RETRY_ATTEMPT * MAX_THREAD, count.get());

        List<DomainEvent> domainEvents = eventStoreDao.findAll();
        Assert.assertEquals(MAX_MESSAGE, domainEvents.size());
        domainEvents.forEach(domainEvent -> {
            Assert.assertEquals((MAX_RETRY_ATTEMPT + INITIAL_ATTEMPT), domainEvent.getAttemptCount());
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
    public void testMessageSerializing() throws IOException, ClassNotFoundException {
        DemoInsurancePolicy policy = new DemoInsurancePolicy(UUID.randomUUID().toString(), "Steve Rogers");
        DemoMessageModel messageModel = new DemoMessageModel(policy);
        Message message = eventStoreService.createEventFromMessage(MessageBuilder.withPayload(messageModel).build());

        DomainEvent domainEvent = eventStoreDao.findAll().get(0);
        String eventId = domainEvent.getId();
        String jsonHeaders = domainEvent.getHeader();
        String jsonPayload = domainEvent.getPayload();
        String payloadClassName = domainEvent.getPayloadClassName();

        Class payloadClass = Class.forName(payloadClassName);
        Message _message = eventStoreService.createMessageFromEvent(eventId, jsonHeaders, jsonPayload, payloadClass);

        Assert.assertTrue(_message.getPayload() instanceof DemoMessageModel);
        Assert.assertEquals(policy.getPolicyId(), ((DemoMessageModel)_message.getPayload()).getDemoInsurancePolicy().getPolicyId());
        Assert.assertEquals(policy.getPolicyHolder(), ((DemoMessageModel)_message.getPayload()).getDemoInsurancePolicy().getPolicyHolder());
    }

}
