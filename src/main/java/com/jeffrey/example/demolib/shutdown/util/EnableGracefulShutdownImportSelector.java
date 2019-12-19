package com.jeffrey.example.demolib.shutdown.util;

import com.jeffrey.example.demolib.shutdown.annotation.EnableGracefulShutdown;
import com.jeffrey.example.demolib.shutdown.config.GracefulShutdownConfig;
import com.jeffrey.example.demolib.shutdown.config.SimpleSecurityConfig;
import com.jeffrey.example.demolib.shutdown.config.StandardSecurityConfig;
import com.jeffrey.example.demolib.shutdown.service.GracefulShutdownService;
import org.springframework.cloud.commons.util.SpringFactoryImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnableGracefulShutdownImportSelector extends SpringFactoryImportSelector<EnableGracefulShutdown> {

    public EnableGracefulShutdownImportSelector() {}

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        String [] imports = super.selectImports(metadata);
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(this.getAnnotationClass().getName(), true));
        List<String> importsList = new ArrayList(Arrays.asList(imports));
        if (!this.isEnabled()) {
            return new String[0];
        } else {
            importsList.add(GracefulShutdownConfig.class.getName());
            importsList.add(GracefulShutdownService.class.getName());

            boolean useSimpleSecurity = attributes.getBoolean("useSimpleSecurity");
            if (useSimpleSecurity) {
                // apply dummy security config to override system/application configured security settings
                // use for debugging/development
                importsList.add(SimpleSecurityConfig.class.getName());
            } else {
                importsList.add(StandardSecurityConfig.class.getName());
            }

        }

        imports = importsList.toArray(new String[0]);
        return imports;
    }

    @Override
    protected boolean isEnabled() {
        return this.getEnvironment().getProperty("com.jeffrey.example.gracefulShutdown.enabled", Boolean.class, Boolean.TRUE);
    }

    @Override
    protected boolean hasDefaultFactory() {
        return true;
    }

}
