package com.jeffrey.example.demoapp;

import com.google.common.io.BaseEncoding;
import com.jeffrey.example.demoapp.config.DemoChannelConfig;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;
import com.jeffrey.example.demoapp.model.DemoInsurancePolicy;
import com.jeffrey.example.demoapp.model.DemoMessageModel;
import com.jeffrey.example.demolib.eventstore.util.ObjectMapperFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.integration.support.json.Jackson2JsonObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.*;
import java.time.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DemoTests {

    @Test
    public void testSerialize0() throws IOException {
        String jsonStringPayload = "{\"demoInsurancePolicy\":{\"policyId\":\"3a3f3566-8ebd-4029-8c33-85ddb67de75d\",\"policyHolder\":\"Steve Rogers\"}}";

        Jackson2JsonObjectMapper mapper = ObjectMapperFactory.getMapper();
        DemoMessageModel payload = mapper.fromJson(jsonStringPayload, DemoMessageModel.class);

        DemoInsurancePolicy policy = payload.getDemoInsurancePolicy();
        Assert.assertEquals("3a3f3566-8ebd-4029-8c33-85ddb67de75d", policy.getPolicyId());
        Assert.assertEquals("Steve Rogers", policy.getPolicyHolder());
    }

    @Test
    public void testSerialize1() throws IOException, ClassNotFoundException {
        Message message = MessageBuilder.withPayload("test").build();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(message);
            oos.flush();

            byte[] bytes = bos.toByteArray();
            String base64String = BaseEncoding.base64().encode(bytes);
            byte[] _bytes = BaseEncoding.base64().decode(base64String);

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(_bytes))) {
                Message _message = (Message)ois.readObject();
                Assert.assertEquals(message.hashCode(), _message.hashCode());
            }
        }
    }

    @Test
    public void testSerialize2() throws IOException {
        Jackson2JsonObjectMapper mapper = ObjectMapperFactory.getMapper();

        Message message = MessageBuilder.withPayload("testing").build();
        String jsonStringHeader = mapper.toJson(message.getHeaders());
        String jsonStringPayload = mapper.toJson(message.getPayload());

        String base64StringHeader = BaseEncoding.base64().encode(jsonStringHeader.getBytes());
        String base64StringPayload = BaseEncoding.base64().encode(jsonStringPayload.getBytes());

        String _jsonStringHeader = new String(BaseEncoding.base64().decode(base64StringHeader));
        String _jsonStringPayload = new String(BaseEncoding.base64().decode(base64StringPayload));

        Map _headers = mapper.fromJson(_jsonStringHeader, Map.class);
        String _payload = mapper.fromJson(_jsonStringPayload, String.class);

        Message _message = MessageBuilder.withPayload(_payload).copyHeaders(_headers).build();
        Assert.assertEquals(message.getPayload(), _message.getPayload());
    }

    @Test
    public void testOffsetDateTime() {
        LocalDateTime utcDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Assert.assertTrue(true);
    }

    @Test
    public void testAqmpMessage() {
        org.springframework.amqp.core.Message amqpMessage = new org.springframework.amqp.core.Message("testing 2".getBytes(), new MessageProperties());
        Map<String,Object> amqpHeaders = amqpMessage.getMessageProperties().getHeaders();
        Assert.assertTrue(true);
    }

    @Test
    public void testCompareLocalDateTime() throws InterruptedException {
        LocalDateTime localDateTime1 = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Thread.sleep(1000);
        LocalDateTime localDateTime2 = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Assert.assertTrue(localDateTime2.isAfter(localDateTime1));
    }

    @Test
    public void testCompareLocalDateTime_withDifferentTimezone() {
        Map<String, String> zoneIdOffsetMap = new HashMap<>();

        Set<String> zoneIds = ZoneId.getAvailableZoneIds();
        for (String zoneIdString:zoneIds) {
            ZoneId zoneId = ZoneId.of(zoneIdString);
            ZoneOffset zoneOffset = ZonedDateTime.now(zoneId).getOffset();
            String offset = zoneOffset.getId().replaceAll("Z", "+00:00");
            zoneIdOffsetMap.put(zoneId.getId(), offset);
        }

        // sort the map by offset
        Map<String, String> sortedZoneIdOffsetMap = new LinkedHashMap<>();
        zoneIdOffsetMap.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByValue())
                .forEachOrdered(e -> sortedZoneIdOffsetMap.put(e.getKey(), e.getValue()));


        Map<String, String> filteredZoneIdOffsetMap = new LinkedHashMap<>();
        zoneIdOffsetMap.entrySet().stream().filter(entry -> {
           if (entry.getValue().equals("+08:00")) {
               return true;
           } else {
               return false;
           }
        }).forEach(entry -> filteredZoneIdOffsetMap.put(entry.getKey(), entry.getValue()));

//        "Europe/London"
//        "GMT"
//        "Asia/Bangkok"
//        "Asia/Tokyo"

//        Clock bangkokClock = Clock.system(ZoneId.of("Asia/Bangkok"));
        Instant instantNow = Instant.now();

        LocalDateTime localDateTime_bkh = LocalDateTime.ofInstant(instantNow, ZoneId.of("Asia/Bangkok"));
        ZonedDateTime zonedDateTime_utc1 = localDateTime_bkh.atZone(ZoneId.of("Asia/Bangkok")).withZoneSameInstant(ZoneId.of("GMT"));

        LocalDateTime localDateTime_tky = LocalDateTime.ofInstant(instantNow, ZoneId.of("Asia/Tokyo"));
        ZonedDateTime zonedDateTime_utc2 = localDateTime_tky.atZone(ZoneId.of("Asia/Tokyo")).withZoneSameInstant(ZoneId.of("GMT"));

        int result = zonedDateTime_utc1.compareTo(zonedDateTime_utc2);
        Assert.assertEquals(0, result);
    }

    @Test
    public void testDomainEventBuilder() {
        DomainEvent domainEvent1 = new DomainEvent.Builder()
                .id("123")
                .header("header")
                .payload("payload")
                .payloadType(String.class.getName())
                .writtenOn(Instant.now())
                .channel(DemoChannelConfig.OUTPUT1)
                .build();

        DomainEvent domainEvent2 = new DomainEvent.Builder()
                .id("123")
                .header("header")
                .payload("payload")
                .payloadType(String.class.getName())
                .writtenOn(Instant.now())
                .channel(DemoChannelConfig.OUTPUT1)
                .build();


        Assert.assertTrue(domainEvent1.hashCode() == domainEvent2.hashCode());
        Assert.assertFalse(domainEvent1.equals(domainEvent2));
    }

}
