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
public class SimpleSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    @Qualifier("gracefulShutdownService")
    GracefulShutdownService gracefulShutdownService;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(
                    new GracefulShutdownProcessingFilter(gracefulShutdownService),
                    ChannelProcessingFilter.class
            )
            .authorizeRequests()
                // permits all endpoints
                .antMatchers("/**")
                .permitAll()
                .and()
            .csrf()
                // disable csrf for all endpoints
                .disable()
            .headers()
                // allow use of frame to same origin urls
                .frameOptions()
                .sameOrigin();
    }

}
