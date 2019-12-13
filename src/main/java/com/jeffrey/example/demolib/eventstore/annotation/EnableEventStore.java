package com.jeffrey.example.demolib.eventstore.annotation;

import com.jeffrey.example.demolib.eventstore.util.EnableEventStoreImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({EnableEventStoreImportSelector.class})
@EnableMongoRepositories(
        basePackages = "com.jeffrey.example.demolib.eventstore.repository"
)
public @interface EnableEventStore {

    // TODO: depends on the DB storage specified by user
    // TODO: add support for JPA

}
