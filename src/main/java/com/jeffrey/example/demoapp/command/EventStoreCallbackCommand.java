package com.jeffrey.example.demoapp.command;

import com.jeffrey.example.demoapp.entity.DomainEvent;

@FunctionalInterface
public interface EventStoreCallbackCommand {

    void pendingEventFetched(DomainEvent domainEvent) throws Exception;

}
