package com.jeffrey.example.demolib.eventstore.util;

import com.google.common.collect.ImmutableMap;
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
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component("ChannelBindingAccessor")
public class ChannelBindingAccessor {

    private Environment environment;

    private ApplicationContext applicationContext;

    private BindingServiceProperties bindingServiceProperties;

    private ImmutableMap<String, String> producerChannelsWithServiceActivatorsMap;

    public ChannelBindingAccessor(
            Environment environment,
            ApplicationContext applicationContext,
            BindingServiceProperties bindingServiceProperties
    ) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.bindingServiceProperties = bindingServiceProperties;
    }

    public ImmutableMap<String, String> getAllProducerChannelsWithConfirmAck() {
        final Map<String, String> producerConfirmAckChannelsMap = new HashMap<>();

        // scan all producer
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable  = applicationContext.getBean(bindableBeanName, Bindable.class);
            for (String binding:bindable.getOutputs()) {
                Object outputBindable = applicationContext.getBean(binding);
                if (outputBindable instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputBindable;
                    String confirmAckChannelName = getConfirmAckChannel(abstractMessageChannel);
                    producerConfirmAckChannelsMap.put(abstractMessageChannel.getBeanName(), confirmAckChannelName);
                }
            }
        }

        return ImmutableMap.copyOf(producerConfirmAckChannelsMap);
    }

    public ImmutableMap<String, ServiceActivatingHandler> getAllServiceActivatingHandlerInputChannels() {
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

    public boolean isErrorChannelEnabled(ImmutableMap<String, ServiceActivatingHandler> serviceActivatingHandlerMap) {
        // FIXME: dynamically look up error channel configuration per output channel
        String producerErrorChannelKey = "spring.cloud.stream.bindings.output.producer.errorChannelEnabled";
        Boolean errorChannelEnabled = environment.getProperty(producerErrorChannelKey, Boolean.class);
        // FIXME: externalize error channel name to configuration
        ServiceActivatingHandler errorChannelHandler = serviceActivatingHandlerMap.get("errorChannel");
        return (errorChannelEnabled!=null && errorChannelEnabled.booleanValue() && errorChannelHandler!=null);
    }

    @Nullable
    public String getServiceActivatingHandlerErrorChannel(ImmutableMap<String, ServiceActivatingHandler> serviceActivatingHandlerMap) {
        // FIXME: externalize error channel name to configuration
        ServiceActivatingHandler errorChannelHandler = serviceActivatingHandlerMap.get("errorChannel");
        if (errorChannelHandler != null) {
            return "errorChannel";
        }
        return null;
    }

    @Deprecated
    public void getAndDiscoverServiceActivatingHandlerIfAbsent() {
        if (producerChannelsWithServiceActivatorsMap != null) return;

        Map<String, String> producerConfirmAckChannelsMap = new HashMap<>();
        Map<String, ServiceActivatingHandler> serviceActivatingHandlerMap = new HashMap<>();

        // scan all producer
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable  = applicationContext.getBean(bindableBeanName, Bindable.class);
            for (String binding:bindable.getOutputs()) {
                Object outputBindable = applicationContext.getBean(binding);
                if (outputBindable instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) outputBindable;
                    String confirmAckChannelName = getConfirmAckChannel(abstractMessageChannel);
                    producerConfirmAckChannelsMap.put(abstractMessageChannel.getBeanName(), confirmAckChannelName);
                }
            }
        }

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

        producerChannelsWithServiceActivatorsMap = ImmutableMap.copyOf(
            producerConfirmAckChannelsMap.entrySet().stream().filter((entry) -> {
                return serviceActivatingHandlerMap.get(entry.getValue()) != null;
            }).collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))
        );
    }

    private enum SupportedBinders {
        rabbit,
        kafka
    }

    protected BindingProperties getBinding(String channelName) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(channelName);
        return bindingProperties;
    }

    protected BinderProperties getBinder(String channelName) {
        final BindingProperties bindingProperties = bindingServiceProperties.getBindings().get(channelName);
        if (bindingProperties == null) return null;

        final String binderName = bindingProperties.getBinder();
        if (binderName == null) return null;

        final BinderProperties binderProperties = bindingServiceProperties.getBinders().get(binderName);
        return binderProperties;
    }

    protected String getConfirmAckChannel(AbstractMessageChannel channel) {
        final String beanName = channel.getBeanName();
        final BindingProperties bindingProperties = getBinding(beanName);
        final BinderProperties binderProperties = getBinder(beanName);

        if (bindingProperties!=null &&
                binderProperties!=null &&
                bindingProperties.getProducer()!=null &&
                bindingProperties.getProducer().isErrorChannelEnabled()
        ) {
            final String binderType = binderProperties.getType();

            switch (SupportedBinders.valueOf(binderType)) {
                case rabbit:
                    String confirmAckChannelKey = String.format(
                            "spring.cloud.stream.%s.bindings.%s.producer.confirmAckChannel",
                            SupportedBinders.rabbit.name(),
                            beanName);
                    String confirmAckChannelName = environment.getProperty(confirmAckChannelKey);
                    return confirmAckChannelName;
                case kafka:
                default:
                    // skip if binder type is un-supported
                    throw new RuntimeException("un-supported binder type for channel: " + channel.getBeanName());
            }

        } else {
            throw new RuntimeException("binding configuration not found for channel: " + channel.getBeanName());
        }
    }

}
