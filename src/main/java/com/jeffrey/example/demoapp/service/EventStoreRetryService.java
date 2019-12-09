package com.jeffrey.example.demoapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventStoreRetryService {

    private RetryTemplate retryTemplate;

    public EventStoreRetryService(
            @Autowired @Qualifier("eventStoreRetryTemplate") RetryTemplate retryTemplate
    ) {
        this.retryTemplate = retryTemplate;
    }

    public void execute(RetryCallback retryCallback) {
        retryTemplate.execute(retryCallback);
    }
}
