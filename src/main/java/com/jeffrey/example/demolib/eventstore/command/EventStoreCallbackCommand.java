package com.jeffrey.example.demolib.eventstore.command;


import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;

@FunctionalInterface
public interface EventStoreCallbackCommand {

    void pendingEventFetched(DomainEvent domainEvent) throws Exception;

}
