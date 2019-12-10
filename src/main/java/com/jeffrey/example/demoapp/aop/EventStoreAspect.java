package com.jeffrey.example.demoapp.aop;

import com.jeffrey.example.demoapp.service.EventStoreService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Aspect
public class EventStoreAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreAspect.class);

    @Autowired
    EventStoreService eventStoreService;

    @Around("@annotation(publisher) && args(message)")
    public Object interceptPublisher(
            ProceedingJoinPoint proceedingJoinPoint,
            org.springframework.integration.annotation.Publisher publisher, Message<?> message) throws Throwable
    {
        String outputChannelBeanName = StringUtils.isEmpty(publisher.channel()) ? publisher.value() : publisher.channel();
        LOGGER.debug("channel bean name: {} ", outputChannelBeanName);

//        String eventId = UUID.randomUUID().toString();
//        message = MessageBuilder
//                .withPayload(message.getPayload())
//                .copyHeaders(message.getHeaders())
//                .setHeader("_eventId", eventId)
//                .build();

        try {
            return proceedingJoinPoint.proceed(new Object[] {message});
        } catch (Throwable t) {
            throw t;
        }
    }

}
