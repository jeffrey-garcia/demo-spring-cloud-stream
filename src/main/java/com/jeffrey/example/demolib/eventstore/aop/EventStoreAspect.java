package com.jeffrey.example.demolib.eventstore.aop;

import com.google.common.collect.ImmutableMap;
import com.jeffrey.example.demolib.eventstore.service.EventStoreService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.util.StringUtils;

@Aspect
public class EventStoreAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreAspect.class);

    @Autowired
    private EventStoreService eventStoreService;

    @Around("@annotation(publisher) && args(message)")
    public Object interceptPublisher(
            ProceedingJoinPoint proceedingJoinPoint,
            org.springframework.integration.annotation.Publisher publisher,
            Message<?> message)
    throws Throwable {
        String outputChannelBeanName = StringUtils.isEmpty(publisher.channel()) ? publisher.value() : publisher.channel();
        LOGGER.debug("output channel name: {} ", outputChannelBeanName);
        ImmutableMap<String,String> confirmAckChannelList = eventStoreService.getProducerChannelsWithServiceActivatorsMap();
        if (!StringUtils.isEmpty(outputChannelBeanName) && confirmAckChannelList.get(outputChannelBeanName) != null) {
            message = eventStoreService.createEventFromMessage(message, outputChannelBeanName);
        }
        return proceedingJoinPoint.proceed(new Object[] {message});
    }

    @Around("@annotation(streamListener)")
    public void interceptConsumer(
            ProceedingJoinPoint proceedingJoinPoint,
            StreamListener streamListener
    ) throws Throwable {
        String inputChannelName = streamListener.value();
        LOGGER.debug("input channel name: {} ", inputChannelName);

        // lookup eventId from header
        String eventId = null;
        Object[] args = proceedingJoinPoint.getArgs();
        if (args!=null && args.length>0) {
            Class<?>[] classes = new Class[args.length];
            for (int i=0; i<args.length; i++) {
                if (args[i] == null) continue;
                classes[i] = args[i].getClass();
                if (classes[i].getName().equals(MessageHeaders.class.getName())) {
                    eventId = ((MessageHeaders)args[i]).get("eventId", String.class);
                    break;
                }
            }
        }

        if (!StringUtils.isEmpty(eventId)) {
            // add deduplication logic here
            if (!eventStoreService.hasEventBeenConsumed(eventId)) {
                proceedingJoinPoint.proceed(args);
                LOGGER.debug("message consumed, eventId: {}", eventId);
                eventStoreService.updateEventAsConsumed(eventId);
            } else {
                LOGGER.warn("event: {} has been consumed, skipping", eventId);
            }
        } else {
            proceedingJoinPoint.proceed(args);
        }
    }

    @Around("@annotation(serviceActivator) && args(message)")
    public void interceptPublisherConfirmOrError(
            ProceedingJoinPoint proceedingJoinPoint,
            org.springframework.integration.annotation.ServiceActivator serviceActivator,
            Message<?> message
    ) throws Throwable {
        String inputChannel = serviceActivator.inputChannel();

        if (!StringUtils.isEmpty(inputChannel)) {
            if (inputChannel.equals(eventStoreService.getErrorChannelName()) && message!=null && (message instanceof ErrorMessage)) {
                /**
                 * Global error messages interceptor
                 *
                 * The error channel gets an ErrorMessage which has a Throwable payload.
                 * Usually the Throwable is a message handling exception with the original
                 * message in the failedMessage property and the exception in the cause.
                 */
                LOGGER.debug("intercepting error channel: {}", inputChannel);
                MessagingException exception = (MessagingException) message.getPayload();

                // capture any publisher error
                if (exception instanceof ReturnedAmqpMessageException) {
                    LOGGER.debug("error sending message to broker: message returned");
                    ReturnedAmqpMessageException amqpMessageException = (ReturnedAmqpMessageException) exception;
                    // producer's message not be accepted by RabbitMQ
                    // the message is returned with a negative ack
                    String errorReason = amqpMessageException.getReplyText();
                    int errorCode = amqpMessageException.getReplyCode();

                    org.springframework.amqp.core.Message amqpMessage = amqpMessageException.getAmqpMessage();
                    String eventId = (String) amqpMessage.getMessageProperties().getHeaders().get("eventId");
                    eventStoreService.updateEventAsReturned(eventId);
                    LOGGER.debug("error reason: {}, error code: {}", errorReason, errorCode);

                } else if (exception instanceof NackedAmqpMessageException) {
                    LOGGER.debug("error sending message to broker: message declined");
                    NackedAmqpMessageException nackedAmqpMessageException = (NackedAmqpMessageException) exception;
                    String errorReason = nackedAmqpMessageException.getNackReason();

                    String eventId = nackedAmqpMessageException.getFailedMessage().getHeaders().get("eventId", String.class);
                    eventStoreService.updateEventAsReturned(eventId);
                    LOGGER.debug("error reason: {}", errorReason);

                } else if (exception instanceof MessageDeliveryException) {
                    LOGGER.debug("error delivering message to consumer");
                    MessageDeliveryException deliveryException = (MessageDeliveryException) exception;
                    String errorReason = deliveryException.getMessage();
                    LOGGER.debug("error reason: {}", errorReason);
                }

            } else if (eventStoreService.getProducerChannelsWithServiceActivatorsMap().containsValue(inputChannel)) {
                /**
                 * Global publisher confirm channel interceptor
                 */
                LOGGER.debug("intercepting publisher's confirm channel: {}", inputChannel);
                //
                Boolean publisherConfirm = message.getHeaders().get("amqp_publishConfirm", Boolean.class);
                if (publisherConfirm != null && publisherConfirm) {
                    // returned message would also produce a positive ack
                    // TODO: require additional safety measure if the returned message failed to be written into DB
                    // See also: MongoEventStoreDao.filterPendingProducerAckOrReturned
                    eventStoreService.updateEventAsProduced(message.getHeaders().get("eventId", String.class));
                    LOGGER.debug("message published: {}", message.getPayload());
                }
            }
        }
        proceedingJoinPoint.proceed(new Object[] {message});
    }

}
