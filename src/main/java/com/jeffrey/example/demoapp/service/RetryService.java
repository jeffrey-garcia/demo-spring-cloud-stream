package com.jeffrey.example.demoapp.service;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
public class RetryService {

    private RetryTemplate retryTemplate;

    private RetryCallback retryCallback;

    public RetryService(RetryTemplate retryTemplate, RetryCallback retryCallback) {
        this.retryTemplate = retryTemplate;
        this.retryCallback = retryCallback;
    }

    public void execute() {
        retryTemplate.execute(retryCallback);
    }
}
