package com.jeffrey.example.demoapp.service;

import com.google.common.collect.ImmutableMap;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreDao;
import com.jeffrey.example.demoapp.util.ChannelBindingAccessor;
import com.jeffrey.example.demoapp.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.IdGenerator;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventStoreService<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Value("${eventstore.retry.autoStart:true}")
    boolean autoStart;

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

    public Message createEventFromMessage(Message message, String outputChannelName) throws IOException {
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
        // should only send to the specific output channel
        Object outputChannelBean = applicationContext.getBean(outputChannelBeanName);
        if (outputChannelBean != null && outputChannelBean instanceof AbstractMessageChannel) {
            AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputChannelBean;
            LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
            abstractMessageChannel.send(message);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    private void postApplicationStartup() {
        eventStoreDao.initializeDb();

        discoverServiceActivatingHandler();

        if (autoStart) {
            eventStoreRetryService.execute((RetryCallback<Void, RuntimeException>) retryContext -> {
                LOGGER.debug("retry count: {}", retryContext.getRetryCount());
                fetchEventAndResend();
                throw new RuntimeException("initiate next retry");
            });
        }
    }

    private ImmutableMap<String, String> producerChannelsWithServiceActivatorsMap = ImmutableMap.of();
    private boolean errorChannelActivated = false;
    private String errorChannelName = null;

    private void discoverServiceActivatingHandler() {
        final ImmutableMap<String, String> producerConfirmAckChannelsMap =
                channelBindingAccessor.getAllProducerChannelsWithConfirmAck();

        final ImmutableMap<String, ServiceActivatingHandler> serviceActivatingHandlerMap =
                channelBindingAccessor.getAllServiceActivatingHandlerInputChannels();

        errorChannelActivated = channelBindingAccessor.isErrorChannelEnabled(serviceActivatingHandlerMap);
        errorChannelName = channelBindingAccessor.getServiceActivatingHandlerErrorChannel(serviceActivatingHandlerMap);

        producerChannelsWithServiceActivatorsMap = ImmutableMap.copyOf(
                producerConfirmAckChannelsMap.entrySet().stream().filter((entry) -> {
                    return serviceActivatingHandlerMap.get(entry.getValue()) != null;
                }).collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))
        );
    }

    public ImmutableMap<String, String> getProducerChannelsWithServiceActivatorsMap() {
        return producerChannelsWithServiceActivatorsMap;
    }

    public boolean isErrorChannelEnabled() {
        return errorChannelActivated;
    }

    public String getErrorChannelName() {
        return errorChannelName;
    }

}
