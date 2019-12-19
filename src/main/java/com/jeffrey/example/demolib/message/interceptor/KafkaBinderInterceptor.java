package com.jeffrey.example.demolib.message.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;

public class KafkaBinderInterceptor extends DefaultChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitBinderInterceptor.class);

    public KafkaBinderInterceptor(BeanFactory beanFactory) {
        // TODO: implement the default command
    }

}
