package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
@DataMongoTest
@Import({EventStoreRepository.class, EventStoreService.class})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EventStoreServiceIT {

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

        DomainEvent domainEvent1 = eventStoreService.createEvent(message);
        DomainEvent domainEvent2 = eventStoreService.createEvent(message);

        Assert.assertEquals(1, eventStoreRepository.findAll().size());
        Assert.assertEquals(domainEvent1, domainEvent2);
    }

}
