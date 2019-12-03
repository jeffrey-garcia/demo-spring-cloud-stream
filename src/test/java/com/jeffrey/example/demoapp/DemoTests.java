package com.jeffrey.example.demoapp;

import com.google.common.io.BaseEncoding;
import com.jeffrey.example.util.ObjectMapperFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.integration.support.json.Jackson2JsonObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

public class DemoTests {

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
    public void compareLocalDateTime() throws InterruptedException {
        LocalDateTime localDateTime1 = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Thread.sleep(1000);
        LocalDateTime localDateTime2 = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Assert.assertTrue(localDateTime2.isAfter(localDateTime1));
    }

}
