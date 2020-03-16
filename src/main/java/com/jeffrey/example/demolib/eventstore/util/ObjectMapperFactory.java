package com.jeffrey.example.demolib.eventstore.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.integration.support.json.Jackson2JsonObjectMapper;

public class ObjectMapperFactory {

    public static Jackson2JsonObjectMapper getMapper() {
        return LazyLoader.instance;
    }

    private static class LazyLoader {
        private static final Jackson2JsonObjectMapper instance = new Jackson2JsonObjectMapper(new ObjectMapper());
    }

}