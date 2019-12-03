package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RunWith(SpringRunner.class)
@DataMongoTest
@Import({EventStoreRepository.class, EventStoreService.class})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EventStoreServiceIT {

    @Value("${eventstore.producer.timeout.seconds:15}")
    private long producerTimeoutInSec;

    @Autowired
    EventStoreRepository eventStoreRepository;

    @Autowired
    EventStoreService eventStoreService;

    @Before
    public void setUp() {
        eventStoreRepository.deleteAll();
    }

    @Test
    public void test() throws IOException {
        Message message = MessageBuilder.withPayload("testing message").build();
        DomainEvent domainEvent1 = eventStoreService.upsertEvent(message);
        DomainEvent domainEvent2 = eventStoreService.upsertEvent(message);
        Assert.assertEquals(2, eventStoreRepository.findAll().size());
    }

    @Test
    public void testFindAllPendingProducerAckEvents() throws IOException, InterruptedException {
        Message message = MessageBuilder.withPayload("testing message").build();
        eventStoreService.upsertEvent(message);
        List<DomainEvent> domainEventList = eventStoreService.findAllPendingProducerAckEvents();
        Assert.assertEquals(0, domainEventList.size());

        Thread.sleep(producerTimeoutInSec * 1000);
        domainEventList = eventStoreService.findAllPendingProducerAckEvents();
        Assert.assertEquals(1, domainEventList.size());

        domainEventList = eventStoreRepository.findAll();
        Assert.assertTrue(domainEventList.get(0).getWrittenOn().isBefore(LocalDateTime.now().minusSeconds(producerTimeoutInSec)));
    }

    @Test
    public void testVolumeSend() {
        Assert.fail("not implemented");
    }

    @Test
    public void testTimeZone() {
        Assert.fail("not implemented");
    }

    @Test
    public void testConcurrentResend() {
        Assert.fail("not implemented");
    }

    @Test
    public void testDuplicatedEvent() {
        Assert.fail("not implemented");
    }

}
