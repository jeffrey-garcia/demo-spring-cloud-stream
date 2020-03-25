package com.jeffrey.example.demolib.eventstore.dao;


import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

/**
 * Interface to be implemented by event store dao which
 * contains database-specific operations which is not generic
 * and can't be defined in {@link AbstractEventStoreDao}
 *
 * Note that it is not usually recommended to be overly
 * dependent to database-specific API, as this represents
 * a potentially lock-in with a particular vendor's DB version,
 * as well as a possibly fragmentation of the event store which
 * causes the ongoing maintenance more effort and prone to error.
 *
 * @see AbstractEventStoreDao
 * @see MongoEventStoreDao
 *
 * @author Jeffrey Garcia Wong
 */
public interface EventStoreDao {

    /**
     * Defines all the necessary operations to initialize the event store's database
     * @param outputChannelNames a {@link Collection} of output channel names
     */
    void initializeDb(Collection<String> outputChannelNames);

    /**
     * Configure the system {@link Clock} for reading/writing records into event store based on
     * a specified timezone
     *
     * @param clock
     */
    void configureClock(Clock clock);

    /**
     * Create a {@link DomainEvent} with the supplied attributes and write it into the event store.
     *
     * @param eventId the unique ID of the event
     * @param header the message's header in json string
     * @param payload the message's payload in json string
     * @param payloadClass the {@link Class} name of the payload
     * @param outputChannelName name of channel where the message is published to
     * @return a {@link DomainEvent}
     */
    DomainEvent createEvent(String eventId, String header, String payload, String payloadClass, String outputChannelName);

    /**
     * Update the timestamp of the {@link DomainEvent} when the message is returned/rejected
     * by remote broker.
     *
     * @param eventId the id of the {@link DomainEvent} to be updated
     * @param outputChannelName name of channel where the message is published to
     * @return the updated {@link DomainEvent}
     */
    DomainEvent updateReturnedTimestamp(String eventId, String outputChannelName);

    /**
     * Update the timestamp of the {@link DomainEvent} when the message is published
     * to remote broker.
     *
     * @param eventId the id of the {@link DomainEvent} to be updated
     * @param outputChannelName name of channel where the message is published to
     * @return the updated {@link DomainEvent}
     */
    DomainEvent updateProducedTimestamp(String eventId, String outputChannelName);

    /**
     * Lookup a {@link DomainEvent} and check whether it has been consumed.
     *
     * @param eventId the id of the {@link DomainEvent} to lookup
     * @param outputChannelName name of channel where the message is published to
     * @return true if the {@link DomainEvent} is consumed, false if otherwise
     */
    boolean hasConsumedTimeStamp(String eventId, String outputChannelName);

    /**
     * Update the timestamp of the {@link DomainEvent} when the message is consumed
     * to remote broker.
     *
     * @param eventId the id of the {@link DomainEvent} to be updated
     * @param outputChannelName name of channel where the message is published to
     * @return the updated {@link DomainEvent}
     */
    DomainEvent updateConsumedTimestamp(String eventId, String outputChannelName);

    /**
     * Fetch any {@link DomainEvent} from the event store which has not been successfully
     * published to/returned by remote broker, once the data is fetched from database, trigger
     * the {@link EventStoreCallbackCommand} to executes the specified callback operation.
     *
     * @param outputChannelName name of channel where the message is published to
     * @param callbackCommand the callback operation to be executed
     */
    void filterPendingProducerAckOrReturned(String outputChannelName, EventStoreCallbackCommand callbackCommand);

    /**
     * Delete all {@link DomainEvent} in the event store
     *
     * @param outputChannelName name of channel where the message is published to
     */
    void deleteAll(String outputChannelName);

    /**
     * Retrieve all {@link DomainEvent} in the event store
     *
     * @return list of {@link DomainEvent} in the event store
     */
    List<DomainEvent> findAll(String outputChannelName);

}
