package com.jeffrey.example.demolib.shutdown.config;

import com.jeffrey.example.demolib.shutdown.filter.GracefulShutdownProcessingFilter;
import com.jeffrey.example.demolib.shutdown.service.GracefulShutdownService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;

@EnableWebSecurity
@Configuration
@Order(1) // take precedence and override the application's configured web security configurer
public class DefaultSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    @Qualifier("gracefulShutdownService")
    GracefulShutdownService gracefulShutdownService;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(
                    new GracefulShutdownProcessingFilter(gracefulShutdownService),
                    ChannelProcessingFilter.class
            );
    }

}
