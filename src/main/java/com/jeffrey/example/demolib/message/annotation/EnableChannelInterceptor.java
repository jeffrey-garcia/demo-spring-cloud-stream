package com.jeffrey.example.demolib.message.annotation;

import com.jeffrey.example.demolib.message.util.EnableChannelInterceptorImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({EnableChannelInterceptorImportSelector.class})
public @interface EnableChannelInterceptor {
    boolean useDefault() default true;
}
