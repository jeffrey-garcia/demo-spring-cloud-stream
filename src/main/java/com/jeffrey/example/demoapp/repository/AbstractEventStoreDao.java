package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEventStoreDao implements EventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventStoreDao.class);

    public abstract DomainEvent createEvent(String eventId, String header, String payload, String payloadClass);

    public abstract DomainEvent updateReturnedTimestamp(String eventId);

    public abstract DomainEvent updateProducedTimestamp(String eventId);

    public abstract DomainEvent updateConsumedTimestamp(String eventId);

    public abstract void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand);

}
