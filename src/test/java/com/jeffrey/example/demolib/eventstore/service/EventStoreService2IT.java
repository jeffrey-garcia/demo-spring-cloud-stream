package com.jeffrey.example.demolib.eventstore.service;

import com.jeffrey.example.demoapp.model.DemoInsurancePolicy;
import com.jeffrey.example.demoapp.model.DemoMessageModel;
import com.jeffrey.example.demolib.eventstore.annotation.EnableEventStore;
import com.jeffrey.example.demolib.eventstore.config.EventStoreConfig;
import com.jeffrey.example.demolib.eventstore.config.MongoDbConfig;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;
import com.jeffrey.example.demolib.eventstore.repository.EventStoreDao;
import com.jeffrey.example.demolib.eventstore.repository.MongoEventStoreDao;
import com.jeffrey.example.demolib.eventstore.util.ChannelBindingAccessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.Publisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Import({
        ChannelBindingAccessor.class,
        MongoDbConfig.class,
        MongoEventStoreDao.class,
        EventStoreConfig.class,
        EventStoreService.class,
        EventStoreRetryService.class
})
@DataMongoTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class EventStoreService2IT {

    @SpringBootApplication
    @EnableEventStore
    @EnableBinding(Processor.class)
    public static class TestProcessor {
        @Autowired
        Processor processor;

        @Publisher(channel = Processor.OUTPUT)
        public boolean sendMessage(Message<?> message) throws IOException {
            return processor.output().send(message);
        }

        @StreamListener(Sink.INPUT)
        public void listen(
                @Payload String messageString,
                @Header(name = "x-death", required = false) Map<?,?> death,
                @Headers MessageHeaders headers
        ) throws Exception {
            // message receiver
        }
    }

    @Value("${com.jeffrey.example.eventstore.retry.backoff.milliseconds:30000}")
    long retryBackoffTimeInMs;

    @Autowired
    EventStoreDao eventStoreDao;

    @Autowired
    EventStoreService eventStoreService;

    @Autowired
    TestProcessor testProcessor;

    @Before
    public void setUp() {
        eventStoreDao.deleteAll();
    }

    @Test
    public void testCreateEvent() throws Exception {
        Message message = MessageBuilder.withPayload("testing message").build();
        eventStoreService.createEventFromMessage(message, Source.OUTPUT);
        eventStoreService.createEventFromMessage(message, Source.OUTPUT);
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

        EventStoreRetryService eventStoreRetryService = new EventStoreRetryService(retryTemplate);
        try {
            eventStoreRetryService.execute(retryCallback);
        } catch (Exception e) { }

        Assert.assertEquals(MAX_RETRY, count.get());
    }

    @Test
    public void testConcurrentRetry_noProducerAck() throws Throwable {
        final int MAX_MESSAGE = 10;
        for (int i=0; i<MAX_MESSAGE; i++) {
            eventStoreService.createEventFromMessage(
                    MessageBuilder.withPayload("testing message " + i).build(), Source.OUTPUT);
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
        EventStoreRetryService eventStoreRetryService = new EventStoreRetryService(retryTemplate);

        final int MAX_THREAD = 8;
        final CountDownLatch lock = new CountDownLatch(MAX_THREAD);
        final Executor executor = Executors.newFixedThreadPool(MAX_THREAD);
        for (int i=0; i<MAX_THREAD; i++) {
            executor.execute(() -> {
                try {
                    eventStoreRetryService.execute(retryCallback);
                } catch (Exception e) { }
                lock.countDown();
            });
        }

        try {
            lock.await();
        } catch (InterruptedException e) { }

        Assert.assertEquals(MAX_RETRY_ATTEMPT * MAX_THREAD, count.get());

        // the attempt count of each message should be equal to max retry + 1 (initial attempt) regardless of how many concurrent retry
        // each retry operation should be atomic and isolated
        List<DomainEvent> domainEvents = eventStoreDao.findAll();
        Assert.assertEquals(MAX_MESSAGE, domainEvents.size());
        domainEvents.forEach(domainEvent -> {
            Assert.assertEquals((MAX_RETRY_ATTEMPT + INITIAL_ATTEMPT), domainEvent.getAttemptCount());
        });
    }

    @Test
    public void testFetchingPendingEventWithTimeZone() throws Exception, InterruptedException {
        // create an event based on GMT+8, then trigger retry operation based on GMT+9
        eventStoreDao.configureClock(Clock.system(ZoneId.systemDefault()));
        Message message = eventStoreService.createEventFromMessage(
                MessageBuilder.withPayload("testing 123").build(), Source.OUTPUT);

        // wait for message to expire before fetching
        Thread.sleep(retryBackoffTimeInMs);
        AtomicInteger counter = new AtomicInteger();

        // simulate the fetching operation is run in a different timezone,
        // pending event should still be retrieved if the event timestamp is persisted in UTC
        eventStoreDao.configureClock(Clock.system(ZoneId.of("Asia/Tokyo")));
        eventStoreDao.filterPendingProducerAckOrReturned((domainEvent) -> counter.incrementAndGet());

        Assert.assertEquals(1, counter.get());
    }

    @Test
    public void testDuplicatedEvent() {
        Assert.fail("not implemented");
    }

    @Test
    public void testMessageSerializing() throws Exception, ClassNotFoundException {
        DemoInsurancePolicy policy = new DemoInsurancePolicy(UUID.randomUUID().toString(), "Steve Rogers");
        DemoMessageModel messageModel = new DemoMessageModel(policy);
        Message message = eventStoreService.createEventFromMessage(
                MessageBuilder.withPayload(messageModel).build(), Source.OUTPUT);

        DomainEvent domainEvent = eventStoreDao.findAll().get(0);
        Message _message = eventStoreService.createMessageFromEvent(domainEvent);

        Assert.assertTrue(_message.getPayload() instanceof DemoMessageModel);
        Assert.assertEquals(policy.getPolicyId(), ((DemoMessageModel)_message.getPayload()).getDemoInsurancePolicy().getPolicyId());
        Assert.assertEquals(policy.getPolicyHolder(), ((DemoMessageModel)_message.getPayload()).getDemoInsurancePolicy().getPolicyHolder());
    }

}
