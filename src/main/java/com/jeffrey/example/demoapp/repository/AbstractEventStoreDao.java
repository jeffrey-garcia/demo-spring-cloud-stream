package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractEventStoreDao implements EventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventStoreDao.class);

    @Autowired
    private ApplicationContext applicationContext;

    public abstract DomainEvent createEvent(String eventId, String header, String payload, String payloadClass);

    public abstract DomainEvent updateReturnedTimestamp(String eventId);

    public abstract DomainEvent updateProducedTimestamp(String eventId);

    public abstract DomainEvent updateConsumedTimestamp(String eventId);

//    public abstract void filterPendingProducerAckOrReturned();

    public abstract void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand);

//    protected Message<?> createMessageFromEvent(String eventId, String jsonHeader, String jsonPayload, Class<T> payloadClazz) throws IOException {
//        Map headers = ObjectMapperFactory.getMapper().fromJson(jsonHeader, Map.class);
//        headers.put("eventId", eventId);
//        T payload = ObjectMapperFactory.getMapper().fromJson(jsonPayload, payloadClazz);
//        Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
//        LOGGER.debug("send message: {}", message);
//        return message;
//    }

//    protected void sendMessage(Message<?> message) {
//        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
//        LOGGER.debug("bindable beans: {}", bindableBeanNames);
//
//        // TODO: should only send to the specific output channel
//        for (String bindableBeanName:bindableBeanNames) {
//            Bindable bindable = (Bindable) applicationContext.getBean(bindableBeanName);
//            for (String binding:bindable.getOutputs()) {
//                Object bindableBean = applicationContext.getBean(binding);
//                if (bindableBean instanceof AbstractMessageChannel) {
//                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) bindableBean;
//                    LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
//                    abstractMessageChannel.send(message);
//                }
//            }
//        }
//    }

}
