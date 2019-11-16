package com.jeffrey.example.demolib.config;

import com.jeffrey.example.demolib.service.ChannelInterceptorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.lang.Nullable;

@Configuration
public class MessageChannelConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageChannelConfig.class);

    @Autowired
    @Qualifier("channelInterceptorService")
    ChannelInterceptorService channelInterceptorService;

    @Bean
    @Qualifier("channelInterceptorConfigurer")
    public BeanPostProcessor channelInterceptorConfigurer() {
        return new BeanPostProcessor() {
            @Nullable
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel)bean;

                    // TODO: refers to demoapp's configuration for message channel that should be intercepted
                    if (beanName.equals("input")) {
                        channelInterceptorService.configureInterceptor(abstractMessageChannel);
                    }
                }
                return bean;
            }
        };
    }
}
