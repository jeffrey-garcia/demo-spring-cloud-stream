package com.example.lib.aop;

import com.example.lib.config.MessageChannelConfig;
import com.example.lib.service.ChannelInterceptorService;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MessageChannelConfig.class, ChannelInterceptorService.class})
public @interface EnableChannelInterceptor {
}
