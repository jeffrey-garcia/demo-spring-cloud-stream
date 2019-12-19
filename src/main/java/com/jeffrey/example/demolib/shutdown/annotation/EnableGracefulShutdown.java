package com.jeffrey.example.demolib.shutdown.annotation;

import com.jeffrey.example.demolib.shutdown.util.EnableGracefulShutdownImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(EnableGracefulShutdownImportSelector.class)
//@EnableAutoConfiguration(exclude = {
//        // disable specific Spring Security and Actuator Managed Security Auto-configuration Classes
//        SecurityAutoConfiguration.class,
//        ManagementWebSecurityAutoConfiguration.class
//})
public @interface EnableGracefulShutdown {
    // apply dummy security config to override system/application configured security settings
    // use for debugging/development
    boolean useSimpleSecurity() default false;
}
