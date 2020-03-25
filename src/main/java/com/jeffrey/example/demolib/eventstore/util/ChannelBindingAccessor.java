package com.jeffrey.example.demolib.eventstore.util;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A handy utility class for accessing any input/output message channels,
 * and the service activating handler associated with these channels.
 *
 * @Author Jeffrey Garcia Wong
 */
@Component("ChannelBindingAccessor")
public class ChannelBindingAccessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelBindingAccessor.class);

    private Environment environment;

    private ApplicationContext applicationContext;

    private BindingServiceProperties bindingServiceProperties;

    public static final String GLOBAL_ERROR_CHANNEL = "errorChannel";

    public static final String GLOBAL_PUBLISHER_CONFIRM_CHANNEL = "publisher-confirm";

    public ChannelBindingAccessor(
            Environment environment,
            ApplicationContext applicationContext,
            BindingServiceProperties bindingServiceProperties
    ) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.bindingServiceProperties = bindingServiceProperties;
    }

    /**
     * Get all producer channels eligible for event store registration.
     *
     * All eligible producer's {@link AbstractMessageChannel} should be configured with the following:
     * <p>
     * confirmAckChannel - a channel to which to send positive delivery acknowledgments (aka publisher confirms).
     * <a href="https://cloud.spring.io/spring-cloud-stream-binder-rabbit/multi/multi__configuration_options.html">confirmAckChannel</a>
     *
     * <p>
     * errorChannel - a channel to receive error or returned/declined message
     * </p>
     *
     * In order for event store to intercept the confirmAckChannel and errorChannel configured
     * for the eligible producer channels, {@link ServiceActivatingHandler} should be created
     * globally.
     *
     * @return {@link ImmutableCollection} a collection of {@link String} names for producer channels
     * configured with errorChannel and publisher-confirm channel. If none producer channel is configured
     * or if the required global {@link ServiceActivatingHandler} does not exist, an empty collection
     * is returned.
     */
    public ImmutableCollection<String> getProducerChannelsWithServiceActivatingHandler() {
        final ImmutableMap<String, String> producerConfirmAckChannelsMap = getAllProducerChannelsWithConfirmAck();
        final ImmutableMap<String, ServiceActivatingHandler> serviceActivatingHandlerMap = getAllServiceActivatingHandlersWithInputChannels();

        if (serviceActivatingHandlerMap.get(ChannelBindingAccessor.GLOBAL_PUBLISHER_CONFIRM_CHANNEL)==null) {
            LOGGER.warn("service activator not configured for: {}", GLOBAL_PUBLISHER_CONFIRM_CHANNEL);
            return ImmutableSet.of();
        }

        if (serviceActivatingHandlerMap.get(ChannelBindingAccessor.GLOBAL_ERROR_CHANNEL)==null) {
            LOGGER.warn("service activator not configured for: {}", GLOBAL_ERROR_CHANNEL);
            return ImmutableSet.of();
        }

        return ImmutableSet.copyOf(producerConfirmAckChannelsMap.keySet());
    }

    ImmutableMap<String, String> getAllProducerChannelsWithConfirmAck() {
        final Map<String, String> producerConfirmAckChannelsMap = new HashMap<>();

        // scan all producer
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable  = applicationContext.getBean(bindableBeanName, Bindable.class);
            for (String binding:bindable.getOutputs()) {
                Object outputBindable = applicationContext.getBean(binding);
                if (outputBindable instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputBindable;
                    String confirmAckChannelName = getProducerConfirmAckChannel(abstractMessageChannel);
                    if (confirmAckChannelName != null) {
                        producerConfirmAckChannelsMap.put(abstractMessageChannel.getBeanName(), confirmAckChannelName);
                    }
                }
            }
        }

        return ImmutableMap.copyOf(producerConfirmAckChannelsMap);
    }

    ImmutableMap<String, ServiceActivatingHandler> getAllServiceActivatingHandlersWithInputChannels() {
        final Map<String, ServiceActivatingHandler> serviceActivatingHandlerMap = new HashMap<>();

        // scan all service activator
        String[] serviceActivatingHandlerBeanNames = applicationContext.getBeanNamesForType(ServiceActivatingHandler.class);
        for (String serviceActivatingHandlerBeanName:serviceActivatingHandlerBeanNames) {
            ServiceActivatingHandler serviceActivatingHandler = applicationContext.getBean(serviceActivatingHandlerBeanName, ServiceActivatingHandler.class);

            // find the origin class that declares the service activator
            String [] parts = serviceActivatingHandlerBeanName.split("\\.");
            Assert.notNull(parts, "service activating handler cannot be parsed");
            Assert.notEmpty(parts, "service activating handler cannot be parsed");
            Assert.isTrue(parts.length >= 2, "service activating handler cannot be parsed");

            // Spring wraps the real class behind proxy, use ClassUtils to return the user-defined class
            Class targetClass = ClassUtils.getUserClass(applicationContext.getBean(parts[0]));
            String targetMethodName = parts[1];

            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                Annotation annotation = AnnotationUtils.findAnnotation(method, ServiceActivator.class);
                if (annotation != null && targetMethodName.equals(method.getName())) {
                    Map attributes = AnnotationUtils.getAnnotationAttributes(annotation);
                    String inputChannelName = (String)attributes.get("inputChannel");
                    serviceActivatingHandlerMap.put(inputChannelName, serviceActivatingHandler);
                }
            }
        }

        return ImmutableMap.copyOf(serviceActivatingHandlerMap);
    }

    private enum SupportedBinders {
        rabbit,
        kafka
    }

    @Nullable
    BindingProperties getBinding(String channelName) {
        Assert.notNull(bindingServiceProperties, "Binding service properties should not be null");
        return bindingServiceProperties.getBindings().get(channelName);
    }

    @Nullable
    String getBinderName(String channelName) {
        BindingProperties bindingProperties = getBinding(channelName);
        Assert.notNull(bindingProperties, "Binding properties should not be null");
        return bindingProperties.getBinder();
    }

    @Nullable
    BinderProperties getBinder(String channelName) {
        String binderName = getBinderName(channelName);
        return binderName==null ? null:bindingServiceProperties.getBinders().get(binderName);
    }

    @Nullable
    ProducerProperties getProducer(String channelName) {
        BindingProperties bindingProperties = getBinding(channelName);
        Assert.notNull(bindingProperties, "Binding properties should not be null");
        return bindingProperties.getProducer();
    }

    @Nullable
    String getProducerConfirmAckChannel(AbstractMessageChannel channel) {
        Assert.notNull(channel, "channel should not be null");
        final String beanName = channel.getBeanName();

        final String binderName = getBinderName(beanName);
        if (binderName == null) {
            LOGGER.debug("no binder configuration for producer: {}", beanName);
            return null; // channel has no binder, skip it
        }

        final BinderProperties binderProperties = getBinder(beanName);
        final ProducerProperties producerProperties = getProducer(beanName);
        Assert.notNull(binderProperties, "Binder properties should not be null");
        Assert.notNull(producerProperties,  "Producer properties should not be null");

        final String binderType = binderProperties.getType();
        switch (SupportedBinders.valueOf(binderType)) {
            case rabbit:
                String confirmAckChannelKey = String.format(
                        "spring.cloud.stream.%s.bindings.%s.producer.confirmAckChannel",
                        SupportedBinders.rabbit.name(),
                        beanName);
                String confirmAckChannelName = environment.getProperty(confirmAckChannelKey);
                if (!GLOBAL_PUBLISHER_CONFIRM_CHANNEL.equals(confirmAckChannelName)) {
                    LOGGER.warn("producer: {} confirmAckChannel: {} not match", beanName, confirmAckChannelName);
                    return null;
                }

                String publisherConfirmsKey = String.format(
                        "spring.cloud.stream.binders.%s.environment.spring.rabbitmq.publisherConfirms",
                        binderName
                );
                String publisherConfirms = environment.getProperty(publisherConfirmsKey);

                String publisherReturnsKey = String.format(
                        "spring.cloud.stream.binders.%s.environment.spring.rabbitmq.publisherReturns",
                        binderName
                );
                String publisherReturns = environment.getProperty(publisherReturnsKey);

                if (StringUtils.isEmpty(confirmAckChannelName)) {
                    LOGGER.debug("confirmAckChannel is not configured for channel: {}", beanName);
                    return null;
                } else {
                    if (!producerProperties.isErrorChannelEnabled()) {
                        LOGGER.debug("error channel not enabled for producer: {} ", beanName);
                        return null;
                    }
                    if (StringUtils.isEmpty(publisherConfirms) || !Boolean.valueOf(publisherConfirms)) {
                        LOGGER.warn("publisherConfirms is not configured, confirmAckChannel will not be functioning properly");
                        return null;
                    }
                    if (StringUtils.isEmpty(publisherReturns) || !Boolean.valueOf(publisherReturns)) {
                        LOGGER.warn("publisherReturns is not configured, confirmAckChannel will not be functioning properly");
                        return null;
                    }
                    return confirmAckChannelName;
                }

            case kafka:
            default:
                // skip if binder type is un-supported
                throw new UnsupportedOperationException("un-supported binder type for channel: " + channel.getBeanName());
        }
    }

    @ServiceActivator(inputChannel = GLOBAL_ERROR_CHANNEL)
    public void error(Message<?> message) {
        LOGGER.debug("on error: {}", message);
    }

    @ServiceActivator(inputChannel = GLOBAL_PUBLISHER_CONFIRM_CHANNEL)
    public void onPublisherConfirm(Message<?> message) {
        LOGGER.debug("on publisher confirm: {}", message);
    }
}
