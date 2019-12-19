package com.jeffrey.example.demolib.message.config;

import com.jeffrey.example.demolib.message.service.ChannelInterceptorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.AbstractMessageChannel;

import javax.annotation.PostConstruct;

@Configuration
public class MessageChannelConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageChannelConfig.class);

    @Autowired
    @Qualifier("channelInterceptorService")
    ChannelInterceptorService channelInterceptorService;

    @Autowired
    BeanFactory beanFactory;

    @Autowired
    ApplicationContext applicationContext;

    @PostConstruct
    void channelInterceptorConfigurer() {
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable = applicationContext.getBean(bindableBeanName, Bindable.class);
            for (String binding:bindable.getInputs()) {
                Object bindableBean = beanFactory.getBean(binding);
                if (bindableBean instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) bindableBean;
                    // automatically lookup app's configuration for input message channel that should be intercepted
                    channelInterceptorService.configureInterceptor(abstractMessageChannel);
                }
            }
        }
    }

}
