package com.jeffrey.example.demoapp.command;

import com.jeffrey.example.demoapp.entity.DomainEvent;

import java.io.IOException;

@FunctionalInterface
public interface EventStoreCallbackCommand<T> {
//    void pendingEventFetched(String eventId, String jsonHeaders, String jsonPayload, Class<T> payloadClass) throws IOException;
    void pendingEventFetched(DomainEvent domainEvent) throws Exception;
}
