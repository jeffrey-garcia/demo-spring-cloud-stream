package com.example.lib.aop;

import com.example.lib.util.EnableChannelInterceptorImportSelector;
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
