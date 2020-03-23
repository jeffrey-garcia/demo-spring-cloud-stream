package com.jeffrey.example.demolib.eventstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The service class for coordinating the retry routine of event store.
 */
@Service("EventStoreRetryService")
public class EventStoreRetryService {
    private final static Logger LOGGER = LoggerFactory.getLogger(EventStoreRetryService.class);

    private RetryTemplate retryTemplate;

    /**
     * Instantiate the event store retry service.
     *
     * @param retryTemplate {@link RetryTemplate}
     */
    public EventStoreRetryService(
            @Autowired @Qualifier("eventStoreRetryTemplate") RetryTemplate retryTemplate
    ) {
        this.retryTemplate = retryTemplate;
    }

    /**
     * Invoke the {@link RetryTemplate} to execute the retry operation.
     * This method is synchronous and blocking.
     *
     * @param retryCallback {@link RetryCallback} associated with a {@link org.springframework.retry.RetryContext}
     */
    public void start(RetryCallback<Void, RuntimeException> retryCallback) {
        try {
            LOGGER.debug("starting retry");
            retryTemplate.execute(retryCallback);
        } catch (Throwable t) { }
    }

    /**
     * Invoke a {@link Collection} of {@link RetryCallback} with an {@link ExecutorService}.
     * The retry operation wrapped in the {@link RetryCallback} will therefore runs in
     * an asynchronous fashion.
     *
     * @param retryCallbacks
     */
    public void startAsync(Collection<RetryCallback<Void, RuntimeException>> retryCallbacks) {
        final ExecutorService executorService = Executors.newFixedThreadPool(retryCallbacks.size());
        for (RetryCallback<Void, RuntimeException> retryCallback:retryCallbacks) {
            executorService.execute(() -> start(retryCallback));
        }
    }
}
