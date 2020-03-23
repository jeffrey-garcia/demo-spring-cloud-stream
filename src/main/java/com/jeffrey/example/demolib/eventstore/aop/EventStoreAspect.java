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

/**
 * An aspect class defining advices which intercepts the producer, consumer and
 * service activator to integrate event store without affecting the business logic
 * @author Jeffrey Garcia Wong
 */
@Aspect
public class EventStoreAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreAspect.class);

    @Autowired
    private EventStoreService eventStoreService;

    /**
     * Intercept the {@link org.springframework.integration.annotation.Publisher} to
     * execute the event store functionality
     *
     * <p>Extract the output channel name and the message (header and payload) from
     * the {@link org.springframework.integration.annotation.Publisher}, then
     * invoke the {@link EventStoreService#createEventFromMessageAndSend(Message, String, ProceedingJoinPoint)}
     * to create an event into the event store and send the {@link Message} to remtoe broker.</p>
     *
     * @param proceedingJoinPoint
     * The location of the {@link ProceedingJoinPoint} where the advice will be executed.
     * @param publisher
     * The {@link org.springframework.integration.annotation.Publisher} which produce the message.
     * @param message
     * The {@link Message} produced by the publisher
     */
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
            return eventStoreService.createEventFromMessageAndSend(message, outputChannelBeanName, proceedingJoinPoint);
        }
        return proceedingJoinPoint.proceed(new Object[] {message});
    }

    /**
     * Intercept the {@link StreamListener} to execute the event store functionality
     *
     * <p>Extract the event id from {@link MessageHeaders} and invoke the {@link EventStoreService}
     * to mark the event as completed into the event store.</p>
     *
     * @param proceedingJoinPoint
     * The location of the {@link ProceedingJoinPoint} where the advice will be executed.
     * @param streamListener
     * The {@link StreamListener} which consume the message.
     */
    @Around("@annotation(streamListener)")
    public void interceptConsumer(
            ProceedingJoinPoint proceedingJoinPoint,
            StreamListener streamListener
    ) throws Throwable {
        String inputChannelName = streamListener.value();
        LOGGER.debug("input channel name: {} ", inputChannelName);

        Object[] args = proceedingJoinPoint.getArgs();

        // lookup eventId and output channel name from header
        String eventId = null;
        String outputChannelName = null;
        if (args!=null && args.length>0) {
            Class<?>[] classes = new Class[args.length];
            for (int i=0; i<args.length; i++) {
                if (args[i] == null) continue;
                classes[i] = args[i].getClass();
                if (classes[i].getName().equals(MessageHeaders.class.getName())) {
                    eventId = ((MessageHeaders)args[i]).get("eventId", String.class);
                    outputChannelName = ((MessageHeaders)args[i]).get("outputChannelName", String.class);
                    break;
                }
            }
        }

        if (StringUtils.isEmpty(eventId) || StringUtils.isEmpty(outputChannelName)) {
            // eventId or outputChannelName is absent, allow consumer to proceed without interception by event store
            proceedingJoinPoint.proceed(args);

        } else {
            if (!eventStoreService.isIgnoreDuplicate()) {
                // allow consumer to proceed without de-duplication
                proceedingJoinPoint.proceed(args);
                LOGGER.debug("message consumed, eventId: {}", eventId);
                eventStoreService.updateEventAsConsumed(eventId, outputChannelName);

            } else {
                if (!eventStoreService.hasEventBeenConsumed(eventId, outputChannelName)) {
                    proceedingJoinPoint.proceed(args);
                    LOGGER.debug("message consumed, eventId: {}", eventId);
                    eventStoreService.updateEventAsConsumed(eventId, outputChannelName);
                } else {
                    LOGGER.warn("event: {} has been consumed, skipping", eventId);
                    // skip the consumer if the event has been consumed
                }
            }
        }
    }

    /**
     * Intercept the error message channel and publisher-confirm channel globally via
     * {@link org.springframework.integration.annotation.ServiceActivator} to execute
     * the event store functionality
     *
     * <p>If the {@link org.springframework.integration.annotation.ServiceActivator}'s
     * input channel is an error channel, the message is not received by the broker,
     * extract the event id from the {@link MessagingException} of the {@link ErrorMessage},
     * then invoke {@link EventStoreService} to update the message as declined/returned in the
     * event store.</p>
     *
     * <p>If the {@link org.springframework.integration.annotation.ServiceActivator}'s
     * input channel is a producer channel, extract the publisher-confirm attribute from
     * {@link MessageHeaders} and if the message is confirm received by the broker, extract
     * the event id from the {@link MessageHeaders}, then invoke {@link EventStoreService}
     * to update the message as published in the event store.</p>
     *
     * @param proceedingJoinPoint
     * The location of the {@link ProceedingJoinPoint} where the advice will be executed.
     * @param serviceActivator
     * The {@link org.springframework.integration.annotation.ServiceActivator} which is the
     * global error message and publisher confirm channel interceptor.
     * @param message
     * The {@link Message} received by the {@link org.springframework.integration.annotation.ServiceActivator}.
     */
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
                    String outputChannelName = message.getHeaders().get("outputChannelName", String.class);
                    eventStoreService.updateEventAsReturned(eventId, outputChannelName);
                    LOGGER.debug("error reason: {}, error code: {}", errorReason, errorCode);

                } else if (exception instanceof NackedAmqpMessageException) {
                    LOGGER.debug("error sending message to broker: message declined");
                    NackedAmqpMessageException nackedAmqpMessageException = (NackedAmqpMessageException) exception;
                    String errorReason = nackedAmqpMessageException.getNackReason();

                    String eventId = nackedAmqpMessageException.getFailedMessage().getHeaders().get("eventId", String.class);
                    String outputChannelName = message.getHeaders().get("outputChannelName", String.class);
                    eventStoreService.updateEventAsReturned(eventId, outputChannelName);
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
                    /**
                     * Returned message would also produce a positive ack, require additional
                     * safety measure if the returned message timestamp failed to be written
                     * into DB
                     *
                     * See also: MongoEventStoreDao.filterPendingProducerAckOrReturned
                     */
                    String eventId = message.getHeaders().get("eventId", String.class);
                    String outputChannelName = message.getHeaders().get("outputChannelName", String.class);
                    eventStoreService.updateEventAsProduced(eventId, outputChannelName);
                    LOGGER.debug("message published: {}", message.getPayload());
                }
            }
        }
        proceedingJoinPoint.proceed(new Object[] {message});
    }

}
