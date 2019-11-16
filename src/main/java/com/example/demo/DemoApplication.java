package com.example.demo;

import com.example.lib.aop.EnableChannelInterceptor;
import com.example.lib.command.ChannelInterceptCommand;
import com.example.lib.service.ChannelInterceptorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

//@EnableChannelInterceptor(useDefault = false)
@EnableChannelInterceptor
@EnableDiscoveryClient
@SpringBootApplication
public class DemoApplication {
	private static final Logger LOGGER = LoggerFactory.getLogger(DemoApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

//	@Autowired
//	BeanFactory beanFactory;
//
//	@Autowired
//	@Qualifier("channelInterceptorService")
//	ChannelInterceptorService interceptorService;
//
//	@Bean
//	public CommandLineRunner commandLineRunner(ApplicationContext applicationContext) {
//		return (args) -> {
//			// Demonstrate how to override the intercept command to integrate with
//			// any custom business logic or application specific handling
//			interceptorService.configureCommand("input", routeToOtherQueueAutoAck);
//		};
//	}
//
//	final ChannelInterceptCommand<Message<?>> routeToOtherQueueAutoAck = (message, messageChannel) -> {
//		final BindingProperties bindingProperties = interceptorService.getBinding("input");
//		final BinderProperties binderProperties = interceptorService.getBinder("input");
//
//		try {
//			// divert the message to another queue for manual follow-up with auto-acknowledgement
//			// apply "deliver at-least-once" approach to diver the message to another queue
//			// without a DLQ
//			String exchangeName = bindingProperties.getDestination();
//			String queueName = "demo-queue-2";
//			String routingKey = "test.event.2";
//
//			Object rabbitMessagingTemplate = beanFactory.getBean("rabbitMessagingTemplate");
//			Method sendAndReceiveMethod = rabbitMessagingTemplate.getClass().getDeclaredMethod(
//					"sendAndReceive",
//					java.lang.String.class,
//					java.lang.String.class,
//					org.springframework.messaging.Message.class);
//
//			// Crash before sending the message to other queue, the message will be requeue
//			// (no message lost and no duplicate message)
//
//			// Crash during the sending of message to other queue, the message will be requeue,
//			// outgoing message is not guaranteed to have arrived the remote queue, there maybe
//			// duplicated message
//			Message<?> reply = (Message<?>) sendAndReceiveMethod.invoke(rabbitMessagingTemplate, exchangeName, routingKey, message);
//
//			// Crash after remote queue acknowledged the receive of message, the message will be requeue,
//			// there will be duplicated message
//			LOGGER.debug("message routed to: {}", queueName);
//
//		} catch (Exception e) {
//			LOGGER.debug("exception occurred, message should be return and re-queue");
//			throw e;
//		}
//
//		return null;
//	};
//
//	final ChannelInterceptCommand<Message<?>> routeToOtherQueueManualAck = (message, messageChannel) -> {
//		final BindingProperties bindingProperties = interceptorService.getBinding("input");
//		final BinderProperties binderProperties = interceptorService.getBinder("input");
//
//		try {
//			// divert the message to another queue for manual follow-up with manual-acknowledgement
//			// apply "deliver at-least-once" approach to diver the message to another queue
//			// without a DLQ
//			Class rabbitChannelClass = Class.forName("com.rabbitmq.client.Channel");
//			Class amqpHeadersClass = Class.forName("org.springframework.amqp.support.AmqpHeaders");
//
//			Field channelField = amqpHeadersClass.getField("CHANNEL");
//			Field deliveryTagField = amqpHeadersClass.getField("DELIVERY_TAG");
//
//			String channelValue = (String)channelField.get(null);
//			String deliveryTagValue = (String)deliveryTagField.get(null);
//
//			Object rabbitChannel = message.getHeaders().get(channelValue, rabbitChannelClass);
//			Long deliveryTag = message.getHeaders().get(deliveryTagValue, Long.class);
//
//			Method basicNackMethod = rabbitChannelClass.getDeclaredMethod("basicNack", long.class, boolean.class, boolean.class);
//
//			try {
//				// divert the message to dead-letter-queue with exponential backoff to avoid infinite retry
//				String exchangeName = bindingProperties.getDestination();
//				String queueName = "demo-queue-2";
//				String routingKey = "test.event.2";
//
//				Object rabbitMessagingTemplate = beanFactory.getBean("rabbitMessagingTemplate");
//				Method sendAndReceiveMethod = rabbitMessagingTemplate.getClass().getDeclaredMethod(
//						"sendAndReceive",
//						java.lang.String.class,
//						java.lang.String.class,
//						org.springframework.messaging.Message.class);
//
//				// Crash before sending the message to other queue, the message will be requeue
//				// (no message lost and no duplicate message)
//
//				// Crash during the sending of message to other queue, the message will be requeue,
//				// outgoing message is not guaranteed to have arrived the remote queue, there maybe
//				// duplicated message
//				Message<?> reply = (Message<?>) sendAndReceiveMethod.invoke(rabbitMessagingTemplate, exchangeName, routingKey, message);
//				LOGGER.debug("message routed to: {}", queueName);
//
//				// Crash after remote queue acknowledged the receive of message, the message will be requeue,
//				// there will be duplicated message
//				basicNackMethod.invoke(rabbitChannel, deliveryTag, false, false);
//				LOGGER.debug("message acknowledged");
//
//			} catch (Exception e) {
//				LOGGER.debug("exception occurred, message should be return and re-queue");
//				basicNackMethod.invoke(rabbitChannel, deliveryTag, false, true);
//				LOGGER.debug("message returned and re-queued");
//			}
//
//		} catch (Exception e) {
//			throw e;
//		}
//
//		return null;
//	};

}
