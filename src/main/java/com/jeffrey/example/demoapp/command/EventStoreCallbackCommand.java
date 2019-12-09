package com.jeffrey.example.demoapp.command;

import java.io.IOException;

@FunctionalInterface
public interface EventStoreCallbackCommand<T> {
    void pendingEventFetched(String eventId, String jsonHeaders, String jsonPayload, Class<T> payloadClass) throws IOException;
}
