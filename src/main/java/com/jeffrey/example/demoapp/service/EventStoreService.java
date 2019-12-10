package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreDao;
import com.jeffrey.example.demoapp.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.IdGenerator;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Service
public class EventStoreService<T,R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Value("${eventstore.retry.autoStart:true}")
    boolean autoStart;

    private ApplicationContext applicationContext;

    private EventStoreRetryService eventStoreRetryService;

    private EventStoreDao eventStoreDao;

    private IdGenerator eventIdGenerator;

    public EventStoreService(
            @Autowired ApplicationContext applicationContext,
            @Autowired @Qualifier("eventIdGenerator") IdGenerator eventIdGenerator,
            @Autowired EventStoreDao eventStoreDao,
            @Autowired EventStoreRetryService eventStoreRetryService
    ) {
        this.applicationContext = applicationContext;
        this.eventIdGenerator = eventIdGenerator;
        this.eventStoreDao = eventStoreDao;
        this.eventStoreRetryService = eventStoreRetryService;
    }

    public Message<?> createEventFromMessage(Message<?> message, String outputChannelName) throws IOException {
        String eventId = eventIdGenerator.generateId().toString();

        // The MessageHeaders.ID and MessageHeaders.TIMESTAMP are read-only headers and cannot be overridden
        // generate eventId and build a new message which insert eventId to message's header
        message = MessageBuilder
                .withPayload(message.getPayload())
                .copyHeaders(message.getHeaders())
                .setHeader("eventId", eventId)
                .build();

        String jsonHeader = ObjectMapperFactory.getMapper().toJson(message.getHeaders());
        String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
        String payloadClassName = message.getPayload().getClass().getName();

        eventStoreDao.createEvent(eventId, jsonHeader, jsonPayload, payloadClassName, outputChannelName);
        return message;
    }

    public DomainEvent updateEventAsReturned(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreDao.updateReturnedTimestamp(eventId);
    }

    public DomainEvent updateEventAsProduced(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreDao.updateProducedTimestamp(eventId);
    }

    public DomainEvent updateEventAsConsumed(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreDao.updateConsumedTimestamp(eventId);
    }

    public void fetchEventAndResend() {
        eventStoreDao.filterPendingProducerAckOrReturned((domainEvent) -> {
            Message<?> message = createMessageFromEvent(domainEvent);
            sendMessage(message, domainEvent.getChannel());
        });
    }

    protected Message<?> createMessageFromEvent(DomainEvent domainEvent) throws IOException, ClassNotFoundException {
        Map headers = ObjectMapperFactory.getMapper().fromJson(domainEvent.getHeader(), Map.class);
        headers.put("eventId", domainEvent.getId());

        Class<T> payloadClass = (Class<T>) Class.forName(domainEvent.getPayloadType());
        T payload = ObjectMapperFactory.getMapper().fromJson(domainEvent.getPayload(), payloadClass);
        Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();

        LOGGER.debug("send message: {}", message);
        return message;
    }

    protected void sendMessage(Message<?> message, String outputChannelBeanName) {
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        LOGGER.debug("bindable beans: {}", bindableBeanNames);

        // should only send to the specific output channel
        Object outputChannelBean = applicationContext.getBean(outputChannelBeanName);
        if (outputChannelBean != null && outputChannelBean instanceof AbstractMessageChannel) {
            AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputChannelBean;
            LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
            abstractMessageChannel.send(message);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void postApplicationStartup() {
        eventStoreDao.initializeDb();

        if (autoStart) {
            eventStoreRetryService.execute((RetryCallback<Void, RuntimeException>) retryContext -> {
                LOGGER.debug("retry count: {}", retryContext.getRetryCount());
                fetchEventAndResend();
                // throw RuntimeException to initiate next retry
                throw new RuntimeException("initiate next retry");
            });
        }
    }

}
