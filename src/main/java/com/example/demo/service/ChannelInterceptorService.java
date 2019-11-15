package com.example.demo.service;

import com.example.demo.command.ChannelInterceptCommand;
import com.example.demo.interceptor.DefaultChannelInterceptor;
import com.example.demo.interceptor.KafkaBinderInterceptor;
import com.example.demo.interceptor.RabbitBinderInterceptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class ChannelInterceptorService {

    private enum SupportedBinders {
        rabbit,
        kafka
    }

    @Autowired
    BeanFactory beanFactory;

    @Autowired
    BindingServiceProperties bindingServiceProperties;

    public Set<String> getBindingServiceKeys() {
        final Map<String, BindingProperties> bindingPropertiesMap = bindingServiceProperties.getBindings();
        return bindingPropertiesMap.keySet();
    }

    public BindingProperties getBinding(String bindingKey) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(bindingKey);
        return bindingProperties;
    }

    public BinderProperties getBinder(String bindingKey) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(bindingKey);
        if (bindingProperties == null) return null;

        final String binderKey = bindingProperties.getBinder();
        if (binderKey == null) return null;

        final BinderProperties binderProperties = bindingServiceProperties.getBinders().get(binderKey);
        return binderProperties;
    }

    public void configureCommand(String channelName, ChannelInterceptCommand<Message<?>> command) {
        Object bean = beanFactory.getBean(channelName);
        if (bean instanceof AbstractMessageChannel) {
            AbstractMessageChannel channel = (AbstractMessageChannel)bean;
            configureInterceptor(channel).configure(command);
        }
    }

    private DefaultChannelInterceptor createInterceptor(AbstractMessageChannel channel) {
        final String beanName = channel.getBeanName();
        final BindingProperties bindingProperties = getBinding(beanName);
        final BinderProperties binderProperties = getBinder(beanName);

        if (bindingProperties!=null && binderProperties!=null) {
            final String binderType = binderProperties.getType();

            switch (SupportedBinders.valueOf(binderType)) {
                case rabbit:
                    DefaultChannelInterceptor rabbitBinderInterceptor = new RabbitBinderInterceptor();
                    channel.addInterceptor(0, rabbitBinderInterceptor);
                    return rabbitBinderInterceptor;
                case kafka:
                    DefaultChannelInterceptor kafkaBinderInterceptor = new KafkaBinderInterceptor();
                    channel.addInterceptor(0, kafkaBinderInterceptor);
                    return kafkaBinderInterceptor;
                default:
                    // skip if binder type is un-supported
                    throw new RuntimeException("un-supported binder type for channel: " + channel.getBeanName());
            }

        } else {
            throw new RuntimeException("binding configuration not found for channel: " + channel.getBeanName());
        }
    }

    /**
     * Configure a new interceptor for the target message channel with default intercept command
     * corresponding to the message channel's binder type
     */
    public DefaultChannelInterceptor configureInterceptor(AbstractMessageChannel channel) {
        if (channel.getChannelInterceptors().size()==0) {
            // no interceptor found for the current channel, so we will configure one for you
            return createInterceptor(channel);
        } else {
            ChannelInterceptor firstInterceptor = channel.getChannelInterceptors().get(0);
            if (!(firstInterceptor instanceof DefaultChannelInterceptor)) {
                // an existing interceptor is found, instead of replacing it we will configure
                // a new interceptor for you with the specified command
                return createInterceptor(channel);
            }
            // there is an existing interceptor found matching and is return to you
            return (DefaultChannelInterceptor) firstInterceptor;
        }
    }

}
