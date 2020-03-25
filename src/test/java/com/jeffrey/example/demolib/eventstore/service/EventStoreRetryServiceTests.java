package com.jeffrey.example.demolib.eventstore.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringJUnit4ClassRunner.class)
public class EventStoreRetryServiceTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreRetryServiceTests.class);

    private static final Random RANDOM = new Random();
    private static final int TEST_CYCLE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 20;
    private static final int MAX_RETRY_CALLBACK = 30;
    private static final int WAIT_TIMEOUT_SECONDS = 5;

    @Test
    public void loop_execute_blocking() {
        for (int i=0; i<TEST_CYCLE; i++) {
            execute_blocking();
        }
    }

    /**
     * Verify executing multiple {@link org.springframework.retry.RetryContext} with single thread
     * runs the retry in serial manner
     */
    public void execute_blocking() {
        final int retryLimit = RANDOM.ints(1, 1, MAX_RETRY_ATTEMPTS).toArray()[0];
        final int retryCallbackCount = RANDOM.ints(1, 1, MAX_RETRY_CALLBACK).toArray()[0];

        final AtomicInteger counter = new AtomicInteger();

        final RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryLimit));

        EventStoreRetryService eventStoreRetryService = new EventStoreRetryService(retryTemplate);
        RetryCallback<Void, RuntimeException> retryCallback = retryContext -> {
            LOGGER.debug(Thread.currentThread().getName());
            counter.incrementAndGet();
            throw new RuntimeException("initiate next retry with backoff period");
        };

        for (int i=0; i<retryCallbackCount; i++) {
            eventStoreRetryService.start(retryCallback);
        }

        Assert.assertEquals(retryLimit * retryCallbackCount, counter.get());
    }

    @Test
    public void loop_execute_nonBlocking() throws InterruptedException {
        for (int i=0; i<TEST_CYCLE; i++) {
            executeAsync_nonBlocking();
        }
    }

    /**
     * Verify executing multiple {@link org.springframework.retry.RetryContext} with multi thread
     * runs the retry in parallel manner
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void executeAsync_nonBlocking() throws InterruptedException {
        final int retryLimit = RANDOM.ints(1, 1, MAX_RETRY_ATTEMPTS).toArray()[0];
        final int retryCallbackCount = RANDOM.ints(1, 1, MAX_RETRY_CALLBACK).toArray()[0];

        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch countDownLatch = new CountDownLatch(retryLimit * retryCallbackCount);

        final RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryLimit));

        EventStoreRetryService eventStoreRetryService = new EventStoreRetryService(retryTemplate);
        List<RetryCallback<Void, RuntimeException>> retryCallbacks = new ArrayList<>();
        for (int i=0; i<retryCallbackCount; i++) {
            RetryCallback<Void, RuntimeException> retryCallback = retryContext -> {
                counter.incrementAndGet();
                countDownLatch.countDown();
                throw new RuntimeException("initiate next retry");
            };
            retryCallbacks.add(retryCallback);
        }
        eventStoreRetryService.startAsync(retryCallbacks);

        countDownLatch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assert.assertEquals(retryLimit * retryCallbackCount, counter.get());
    }
}
