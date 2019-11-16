package com.jeffrey.example.demolib.service;

import com.jeffrey.example.demolib.command.ChannelInterceptCommand;
import com.jeffrey.example.demolib.interceptor.DefaultChannelInterceptor;
import com.jeffrey.example.demolib.interceptor.KafkaBinderInterceptor;
import com.jeffrey.example.demolib.interceptor.RabbitBinderInterceptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Qualifier("channelInterceptorService")
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

    public BindingProperties getBinding(String channelName) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(channelName);
        return bindingProperties;
    }

    public BinderProperties getBinder(String channelName) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(channelName);
        if (bindingProperties == null) return null;

        final String binderName = bindingProperties.getBinder();
        if (binderName == null) return null;

        final BinderProperties binderProperties = bindingServiceProperties.getBinders().get(binderName);
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
                    // ensure highest execution priority by setting it to the first element in the channel interceptor list
                    channel.addInterceptor(0, rabbitBinderInterceptor);
                    return rabbitBinderInterceptor;
                case kafka:
                    DefaultChannelInterceptor kafkaBinderInterceptor = new KafkaBinderInterceptor();
                    // ensure highest execution priority by setting it to the first element in the channel interceptor list
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
