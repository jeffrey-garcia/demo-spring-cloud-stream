package com.jeffrey.example.demolib.shutdown.annotation;

import com.jeffrey.example.demolib.eventstore.annotation.EnableEventStore;
import com.jeffrey.example.demolib.message.annotation.EnableChannelInterceptor;
import com.jeffrey.example.demolib.shutdown.util.EnableGracefulShutdownImportSelector;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(EnableGracefulShutdownImportSelector.class)
@EnableEventStore
@EnableChannelInterceptor
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class
})
public @interface EnableGracefulShutdown {

    @AliasFor(
            annotation = EnableAutoConfiguration.class,
            attribute = "exclude"
    )
    Class<?>[] suppressAutoConfiguration() default {
            SecurityAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            MongoAutoConfiguration.class,
            MongoDataAutoConfiguration.class
    };

    @AliasFor(
            annotation = EnableChannelInterceptor.class,
            attribute = "useDefault"
    )
    boolean useDefaultChannelInterceptor() default true;

}
