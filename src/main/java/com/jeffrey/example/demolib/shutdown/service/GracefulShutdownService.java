package com.jeffrey.example.demolib.shutdown.service;


import com.netflix.hystrix.Hystrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import rx.schedulers.Schedulers;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service("gracefulShutdownService")
public class GracefulShutdownService implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownService.class);

    private AtomicBoolean isInvoked = new AtomicBoolean(false);

    private ApplicationContext applicationContext;

    public GracefulShutdownService(
            @Autowired ApplicationContext applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    @Async
    public void invoke(final int exitCode, final int shutdownHookTimeout) {
        if (isInvoked.get()) {
            return;
        } else {
            isInvoked.set(true);
        }

        LOGGER.debug("Suspending shutdown for {} ms", shutdownHookTimeout);
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        // delay the shutdown at best effort!
        executor.schedule(() -> {
            LOGGER.debug("Shutdown hook time's up!");

            LOGGER.debug("Shutdown hystrix and its schedulers");
            Hystrix.reset();
            Schedulers.shutdown();

            LOGGER.debug("Shutdown application context");
            ((ConfigurableApplicationContext)applicationContext).close();

            LOGGER.debug("Shutdown the shutdown scheduler");
            executor.shutdown();

            LOGGER.debug("System exit {}", exitCode);
            System.exit(exitCode);

        }, shutdownHookTimeout, TimeUnit.MILLISECONDS);
    }

    public boolean isInvoked() {
        return isInvoked.get();
    }

    @Override
    public void destroy() throws Exception {
        LOGGER.debug("Spring container is destroyed");
    }
}
