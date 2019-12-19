package com.jeffrey.example.demolib.shutdown.annotation;

import com.jeffrey.example.demolib.eventstore.annotation.EnableEventStore;
import com.jeffrey.example.demolib.message.annotation.EnableChannelInterceptor;
import com.jeffrey.example.demolib.shutdown.util.EnableGracefulShutdownImportSelector;
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
public @interface EnableGracefulShutdown {

    boolean useSimpleSecurity() default false;

//    @AliasFor(
//            annotation = EnableChannelInterceptor.class,
//            attribute = "useDefault"
//    )
//    boolean useDefaultChannelInterceptor() default true;

}
