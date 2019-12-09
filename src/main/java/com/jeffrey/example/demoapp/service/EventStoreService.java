package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreDao;
import com.jeffrey.example.demoapp.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Service
public class EventStoreService<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Autowired
    ApplicationContext applicationContext;

    private EventStoreDao eventStoreDao;

    private IdGenerator eventIdGenerator = new AlternativeJdkIdGenerator();

    public EventStoreService(
            EventStoreDao eventStoreDao
    ) {
        this.eventStoreDao = eventStoreDao;
    }

    public Message<?> createEventFromMessage(Message<?> message) throws IOException {
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

        eventStoreDao.createEvent(eventId, jsonHeader, jsonPayload, payloadClassName);
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
        eventStoreDao.filterPendingProducerAckOrReturned((eventId, jsonHeaders, jsonPayload, payloadClass) -> {
            Message<?> message = createMessageFromEvent(eventId, jsonHeaders, jsonPayload, payloadClass);
            sendMessage(message);
        });
    }

    protected Message<?> createMessageFromEvent(String eventId, String jsonHeader, String jsonPayload, Class<T> payloadClass) throws IOException {
        Map headers = ObjectMapperFactory.getMapper().fromJson(jsonHeader, Map.class);
        headers.put("eventId", eventId);

        T payload = ObjectMapperFactory.getMapper().fromJson(jsonPayload, payloadClass);
        Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();

        LOGGER.debug("send message: {}", message);
        return message;
    }

    protected void sendMessage(Message<?> message) {
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        LOGGER.debug("bindable beans: {}", bindableBeanNames);

        // TODO: should only send to the specific output channel
        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable = (Bindable) applicationContext.getBean(bindableBeanName);
            for (String binding:bindable.getOutputs()) {
                Object bindableBean = applicationContext.getBean(binding);
                if (bindableBean instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) bindableBean;
                    LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
                    abstractMessageChannel.send(message);
                }
            }
        }
    }

}
