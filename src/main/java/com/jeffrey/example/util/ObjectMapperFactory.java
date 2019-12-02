package com.jeffrey.example.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.integration.support.json.Jackson2JsonObjectMapper;

public class ObjectMapperFactory {

    public static Jackson2JsonObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return new Jackson2JsonObjectMapper(mapper);
    }

}