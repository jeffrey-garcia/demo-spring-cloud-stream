package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class MessagingConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingConfig.class);

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

    @Bean
    @Qualifier("demoMessageChannelConfigurer")
    public BeanPostProcessor demoMessageChannelConfigurer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel)bean;

                    // TODO: add the custom handling to iterate through the binding service properties
                    final BindingProperties bindingProperties = getBinding(beanName);

                    // ensure highest execution priority by setting it to
                    // the first element in the channel interceptor list
                    if (bindingProperties != null && beanName.equals("input"))
                        abstractMessageChannel.addInterceptor(0, new ChannelInterceptor() {
                            @Nullable
                            @Override
                            public Message<?> preSend(Message<?> message, MessageChannel channel) {

                                final String binderType = getBinder(beanName).getType();

                                switch (SupportedBinders.valueOf(binderType)) {
                                    case rabbit:
                                        // at-least once approach for diverting the message to another queue
//                                        try {
//                                            Class rabbitChannelClass = Class.forName("com.rabbitmq.client.Channel");
//                                            Class amqpHeadersClass = Class.forName("org.springframework.amqp.interceptor.AmqpHeaders");
//
//                                            Field channelField = amqpHeadersClass.getField("CHANNEL");
//                                            Field deliveryTagField = amqpHeadersClass.getField("DELIVERY_TAG");
//
//                                            String channelValue = (String)channelField.get(null);
//                                            String deliveryTagValue = (String)deliveryTagField.get(null);
//
//                                            Object rabbitChannel = message.getHeaders().get(channelValue, rabbitChannelClass);
//                                            Long deliveryTag = message.getHeaders().get(deliveryTagValue, Long.class);
//
//                                            Method basicNackMethod = rabbitChannelClass.getDeclaredMethod("basicNack", long.class, boolean.class, boolean.class);
//
//                                            try {
//                                                // TODO: divert the message to dead-letter-queue with exponential backoff to avoid infinite retry
//                                                String exchangeName = bindingProperties.getDestination();
//                                                String queueName = "demo-queue-2";
//                                                String routingKey = "test.event.2";
//
//                                                Object rabbitMessagingTemplate = beanFactory.getBean("rabbitMessagingTemplate");
//                                                Method sendAndReceiveMethod = rabbitMessagingTemplate.getClass().getDeclaredMethod(
//                                                        "sendAndReceive",
//                                                        java.lang.String.class,
//                                                        java.lang.String.class,
//                                                        org.springframework.messaging.Message.class);
//
//                                                Message<?> reply = (Message<?>) sendAndReceiveMethod.invoke(rabbitMessagingTemplate, exchangeName, routingKey, message);
//                                                LOGGER.debug("message routed to: {}", queueName);
//
//                                                // TODO: if crash at this line (before message acknowledgement) can lead to duplicate message
//                                                basicNackMethod.invoke(rabbitChannel, deliveryTag, false, false);
//                                                LOGGER.debug("message acknowledged");
//
//                                            } catch (Exception e) {
//                                                LOGGER.error(e.getMessage(), e);
//
//                                                basicNackMethod.invoke(rabbitChannel, deliveryTag, false, true);
//                                                LOGGER.debug("message returned and re-queued");
//                                            }
//
//                                        } catch (Exception e) {
//                                            LOGGER.error(e.getMessage(), e);
//                                        }
//                                        break;
                                        throw new AmqpRejectAndDontRequeueException("throw exception while intercepting");
//                                        throw new RuntimeException("exception while intercepting");

                                    case kafka:
                                        // TODO: to be implemented with local Kakfa's docker
                                    default:
                                        break;
                                }

                                /**
                                 * Default Behavior:
                                 * - the state of the message remains as NACK until app restart
                                 * - MessageDeliveryException will be thrown in the message container
                                 */
                                return null;
//                                return message;
                            }
                        });
                }

                return bean;
            }
        };
    }

}
