package com.jeffrey.example.demolib.eventstore.service;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;
import com.jeffrey.example.demolib.eventstore.repository.EventStoreDao;
import com.jeffrey.example.demolib.eventstore.util.ChannelBindingAccessor;
import com.jeffrey.example.demolib.eventstore.util.ObjectMapperFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service("EventStoreService")
public class EventStoreService<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Value("${com.jeffrey.example.eventstore.retry.autoStart:true}")
    boolean autoStart;

    @Value("${com.jeffrey.example.eventstore.consumer.ignoreDuplicate:false}")
    boolean ignoreDuplicate;

    private ApplicationContext applicationContext;

    private ChannelBindingAccessor channelBindingAccessor;

    private EventStoreRetryService eventStoreRetryService;

    private EventStoreDao eventStoreDao;

    private IdGenerator eventIdGenerator;

    public EventStoreService(
            @Autowired ApplicationContext applicationContext,
            @Autowired ChannelBindingAccessor channelBindingAccessor,
            @Autowired @Qualifier("eventIdGenerator") IdGenerator eventIdGenerator,
            @Autowired EventStoreDao eventStoreDao,
            @Autowired EventStoreRetryService eventStoreRetryService
    ) {
        this.applicationContext = applicationContext;
        this.channelBindingAccessor = channelBindingAccessor;
        this.eventIdGenerator = eventIdGenerator;
        this.eventStoreDao = eventStoreDao;
        this.eventStoreRetryService = eventStoreRetryService;
    }

    /**
     * Write the message into event store and send it to the specified output channel.
     * The method is annotated with {@link Transactional} to ensure the atomic behavior
     * of both writing and sending operations, if any of the operation throw exception
     * or encounter error, the write to the event store can be rollback and the original
     * client should be informed with the failure and retry the request.
     *
     * @param message the {@link Message} to send
     * @param outputChannelName the name of the output channel the {@link Message} is being sent to
     * @param proceedingJoinPoint the {@link ProceedingJoinPoint} advice to execute at the {@link org.springframework.aop.Pointcut}
     * @return a generic {@link Object} returned by the {@link ProceedingJoinPoint} if any
     * @throws Throwable a generic {@link Throwable} thrown by the {@link ProceedingJoinPoint} if any
     */
    @Transactional
    public Object createEventFromMessageAndSend(Message message, String outputChannelName, ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        message = createEventFromMessage(message, outputChannelName);
        return proceedingJoinPoint.proceed(new Object[] {message});
    }

    /**
     * Write the {@link Message} into event store and generate an eventId.
     *
     * The {@link org.springframework.messaging.MessageHeaders#ID} and
     * {@link org.springframework.messaging.MessageHeaders#TIMESTAMP} are
     * read-only headers and cannot be overridden, build a new message
     * which insert eventId to message's header.
     *
     * @param message the {@link Message} to send
     * @param outputChannelName the name of the output channel the {@link Message} is being sent to
     * @return the re-generated {@link Message}
     * @throws IOException if any error encountered during the message conversion
     */
    Message createEventFromMessage(Message message, String outputChannelName) throws IOException {
        String eventId = eventIdGenerator.generateId().toString();

        message = MessageBuilder
                .withPayload(message.getPayload())
                .copyHeaders(message.getHeaders())
                .setHeader("eventId", eventId)
                .setHeader("outputChannelName", outputChannelName)
                .build();

        String jsonHeader = ObjectMapperFactory.getMapper().toJson(message.getHeaders());
        String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
        String payloadClassName = message.getPayload().getClass().getName();

        eventStoreDao.createEvent(eventId, jsonHeader, jsonPayload, payloadClassName, outputChannelName);
        return message;
    }

    /**
     * Update an event as returned (remote broker refuse to take the message).
     *
     * @param eventId the event's ID to update
     * @param outputChannelName the name of the output channel the event was written
     * @return {@link DomainEvent} retrieved from event store
     * @throws NullPointerException if eventId or outputChannelName is null
     */
    public DomainEvent updateEventAsReturned(String eventId, String outputChannelName) throws NullPointerException {
        // FIXME: use assertion instead of throwing runtime exception
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        if (StringUtils.isEmpty(outputChannelName)) {
            throw new NullPointerException("outputChannelName should not be null");
        }
        return eventStoreDao.updateReturnedTimestamp(eventId, outputChannelName);
    }

    /**
     * Update an event as produced (successfully sent and received by remote broker).
     *
     * @param eventId the event's ID to update
     * @param outputChannelName the name of the output channel the event was written
     * @return {@link DomainEvent} retrieved from event store
     * @throws NullPointerException if eventId or outputChannelName is null
     */
    public DomainEvent updateEventAsProduced(String eventId, String outputChannelName) throws NullPointerException {
        // FIXME: use assertion instead of throwing runtime exception
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        if (StringUtils.isEmpty(outputChannelName)) {
            throw new NullPointerException("outputChannelName should not be null");
        }
        return eventStoreDao.updateProducedTimestamp(eventId, outputChannelName);
    }

    /**
     * Query whether an event had been consumed.
     *
     * @param eventId the event's ID to query
     * @param outputChannelName the name of the output channel the event was written
     * @return true if the message had been successfully processed by consumer, false if otherwise
     * @throws NullPointerException
     */
    public boolean hasEventBeenConsumed(String eventId, String outputChannelName) throws NullPointerException {
        // FIXME: use assertion instead of throwing runtime exception
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        if (StringUtils.isEmpty(outputChannelName)) {
            throw new NullPointerException("outputChannelName should not be null");
        }
        return eventStoreDao.hasConsumedTimeStamp(eventId, outputChannelName);
    }

    /**
     * Update an event as consumed (message successfully processed by consumer).
     *
     * @param eventId the event's ID to update
     * @param outputChannelName the name of the output channel the event was written
     * @return {@link DomainEvent} retrieved from event store
     * @throws NullPointerException if eventId or outputChannelName is null
     */
    public DomainEvent updateEventAsConsumed(String eventId, String outputChannelName) throws NullPointerException {
        // FIXME: use assertion instead of throwing runtime exception
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        if (StringUtils.isEmpty(outputChannelName)) {
            throw new NullPointerException("outputChannelName should not be null");
        }
        return eventStoreDao.updateConsumedTimestamp(eventId, outputChannelName);
    }

    /**
     * Lookup for any {@link DomainEvent} eligible for resending (any message that is un-certain to
     * have reached and accepted by the remote broker.
     *
     * @param outputChannelName the name of the output channel to lookup
     */
    void fetchEventAndResend(String outputChannelName) {
        eventStoreDao.filterPendingProducerAckOrReturned(outputChannelName, (domainEvent) -> {
            Message<?> message = createMessageFromEvent(domainEvent);
            sendMessage(message, domainEvent.getChannel());
        });
    }

    /**
     * Convert the {@link DomainEvent} to {@link Message} for subsequent resending.
     *
     * @param domainEvent the {@link DomainEvent} eligible for resending
     * @return the {@link Message} converted from the {@link DomainEvent}
     * @throws IOException if the conversion throw error
     * @throws ClassNotFoundException
     */
    Message<?> createMessageFromEvent(DomainEvent domainEvent) throws IOException, ClassNotFoundException {
        Map headers = ObjectMapperFactory.getMapper().fromJson(domainEvent.getHeader(), Map.class);
        headers.put("eventId", domainEvent.getId());

        Class<T> payloadClass = (Class<T>) Class.forName(domainEvent.getPayloadType());
        T payload = ObjectMapperFactory.getMapper().fromJson(domainEvent.getPayload(), payloadClass);
        Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();

        LOGGER.debug("assemble message: {}", message);
        return message;
    }

    /**
     * Resend the {@link Message} to the specified {@link AbstractMessageChannel}
     *
     * @param message the {@link Message} to resend
     * @param outputChannelBeanName the output channel name for which the message will be send to
     */
    void sendMessage(Message<?> message, String outputChannelBeanName) {
        // should only send to the specific output channel
        Object outputChannelBean = applicationContext.getBean(outputChannelBeanName);
        if (outputChannelBean != null && outputChannelBean instanceof AbstractMessageChannel) {
            AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputChannelBean;
            LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
            abstractMessageChannel.send(message);
        }
    }

    private ImmutableCollection<String> registeredProducerChannels = ImmutableSet.of();

    @EventListener(ApplicationReadyEvent.class)
    protected void postApplicationStartup() {
        ImmutableCollection<String> eligibleProducerChannels = discoverEligibleProducerChannels();
        eventStoreDao.initializeDb(eligibleProducerChannels);
        if (autoStart) {
            configureAndStartRetry(eligibleProducerChannels);
        }
        registeredProducerChannels = eligibleProducerChannels;
    }

    /**
     * Configure the {@link RetryCallback} and start it asynchronously.
     *
     * @param outputChannelNames a {@link Collection} of output channel names to before configured with retry
     */
    private void configureAndStartRetry(Collection<String> outputChannelNames) {
        if (outputChannelNames.size() > 0) {
            List<RetryCallback<Void, RuntimeException>> retryCallbacks = new ArrayList<>();
            for (String outputChannelName:outputChannelNames) {
                RetryCallback<Void, RuntimeException> retryCallback = retryContext -> {
                    LOGGER.debug("retry count: {}", retryContext.getRetryCount());
                    LOGGER.debug("output channel: {}", outputChannelName);
                    fetchEventAndResend(outputChannelName);
                    throw new RuntimeException("initiate next retry");
                };
                retryCallbacks.add(retryCallback);
            }
            eventStoreRetryService.startAsync(retryCallbacks);
        }
    }

    /**
     * Initial startup routine to discover all producer channels eligible for event store registration.
     *
     * @return {@link ImmutableCollection} a collection of {@link String} names for eligible producer channels.
     */
    private ImmutableCollection<String> discoverEligibleProducerChannels() {
        return channelBindingAccessor.getProducerChannelsWithServiceActivatingHandler();
    }

    public ImmutableCollection getRegisteredProducerChannels() {
        return registeredProducerChannels;
    }

    public boolean isIgnoreDuplicate() {
        return ignoreDuplicate;
    }

}
