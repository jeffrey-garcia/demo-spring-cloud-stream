package com.jeffrey.example.demolib.shutdown.config;

import com.jeffrey.example.demolib.shutdown.filter.GracefulShutdownProcessingFilter;
import com.jeffrey.example.demolib.shutdown.service.GracefulShutdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import javax.servlet.DispatcherType;
import java.util.Collections;

@RefreshScope
@Configuration
public class GracefulShutdownConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownConfig.class);

    @Value("${com.jeffrey.example.gracefulShutdown.shutdownHook.timeoutMillis:5000}")
    private int shutdownHookTimeout;

    @Autowired
    @Qualifier("gracefulShutdownProcessingFilter")
    GracefulShutdownProcessingFilter gracefulShutdownProcessingFilter;

    @Bean("gracefulShutdownFilterRegistrar")
    public FilterRegistrationBean<GracefulShutdownProcessingFilter> gracefulShutdownFilterRegistrar() {
        // all incoming request will be intercept by this filter which possess the highest priority in spring security filter chain
        FilterRegistrationBean<GracefulShutdownProcessingFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(gracefulShutdownProcessingFilter);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        filterRegistrationBean.setEnabled(true);
        filterRegistrationBean.setUrlPatterns(Collections.singletonList("/*"));
        filterRegistrationBean.setDispatcherTypes(DispatcherType.REQUEST);
        return filterRegistrationBean;
    }

    @RefreshScope
    @Bean("sigIntHandler")
    public SignalHandler sigIntHandler(
            @Autowired ApplicationContext context,
            @Autowired GracefulShutdownService gracefulShutdownService)
    {
        LOGGER.debug("Registering signal interrupt handler...");
        return Signal.handle(new Signal("INT"), new SignalHandler() {
            private static final int SIGINT_EXIT_CODE = 130;

            @Override
            public void handle(Signal signal) {
                LOGGER.debug("Signal interrupt fired");
                gracefulShutdownService.invoke(SIGINT_EXIT_CODE, shutdownHookTimeout);
            }
        });
    }

    @RefreshScope
    @Bean("sigTermHandler")
    public SignalHandler sigTermHandler(
            @Autowired ApplicationContext context,
            @Autowired GracefulShutdownService gracefulShutdownService)
    {
        LOGGER.debug("Registering signal interrupt handler...");
        return Signal.handle(new Signal("TERM"), new SignalHandler() {
            private static final int SIGTERM_EXIT_CODE = 143;

            @Override
            public void handle(Signal signal) {
                LOGGER.debug("Signal termination fired");
                gracefulShutdownService.invoke(SIGTERM_EXIT_CODE, shutdownHookTimeout);
            }
        });
    }

    @Bean("gracefulShutdownContainerCloseListener")
    ApplicationListener gracefulShutdownContainerCloseListener() {
        return (ApplicationListener<ContextClosedEvent>) contextClosedEvent -> {
            LOGGER.debug("Spring container is closed");
        };
    }

}
