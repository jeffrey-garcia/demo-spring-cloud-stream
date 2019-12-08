package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreDao;
import com.jeffrey.example.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class EventStoreService<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    private EventStoreDao eventStoreDao;

    @Autowired
    ApplicationContext applicationContext;

    public EventStoreService(@Autowired EventStoreDao eventStoreDao) {
        this.eventStoreDao = eventStoreDao;
    }

    public Message<?> createEventFromMessage(Message<?> message) throws IOException {
        String eventId = UUID.randomUUID().toString();

        // generate eventId and return insert to message's header
        MessageHeaderAccessor accessor = MessageHeaderAccessor.getMutableAccessor(message);
        accessor.setHeader("eventId", eventId);
        MessageHeaders messageHeaders = accessor.getMessageHeaders();
        message = MessageBuilder.fromMessage(message).copyHeaders(messageHeaders).build();

        String jsonHeader = ObjectMapperFactory.getMapper().toJson(message.getHeaders());
        String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
        String payloadClass = message.getPayload().getClass().getName();

        eventStoreDao.createEvent(eventId, jsonHeader, jsonPayload, payloadClass);
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

//    public void fetchEventAndResend2() {
//        eventStoreDao.filterPendingProducerAckOrReturned((eventId, jsonHeaders, jsonPayload, payloadClass) -> {
//            Message<?> message = createMessageFromEvent(eventId, jsonHeaders, jsonPayload, payloadClass);
//            sendMessage(message);
//        });
//    }

    private Message<?> createMessageFromEvent(String eventId, String jsonHeader, String jsonPayload, Class<T> payloadClazz) throws IOException {
        Map headers = ObjectMapperFactory.getMapper().fromJson(jsonHeader, Map.class);
        headers.put("eventId", eventId);
        T payload = ObjectMapperFactory.getMapper().fromJson(jsonPayload, payloadClazz);
        Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
        LOGGER.debug("send message: {}", message);
        return message;
    }

    private void sendMessage(Message<?> message) {
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
