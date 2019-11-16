package com.jeffrey.example.demolib.util;

import com.jeffrey.example.demolib.aop.EnableChannelInterceptor;
import com.jeffrey.example.demolib.config.MessageChannelConfig;
import com.jeffrey.example.demolib.service.ChannelInterceptorService;
import org.springframework.cloud.commons.util.SpringFactoryImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnableChannelInterceptorImportSelector extends SpringFactoryImportSelector<EnableChannelInterceptor> {

    public EnableChannelInterceptorImportSelector() {}

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        if (!this.isEnabled()) {
            return new String[0];
        } else {
            String [] imports = super.selectImports(metadata);
            AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(this.getAnnotationClass().getName(), true));
            boolean useDefault = attributes.getBoolean("useDefault");
            List<String> importsList = new ArrayList(Arrays.asList(imports));
            importsList.add(ChannelInterceptorService.class.getName());
            if (useDefault) {
                importsList.add(MessageChannelConfig.class.getName());
            }
            imports = importsList.toArray(new String[0]);
            return imports;
        }
    }

    @Override
    protected boolean isEnabled() {
        return this.getEnvironment().getProperty("com.example.channel.interceptor", Boolean.class, Boolean.TRUE);
    }

    @Override
    protected boolean hasDefaultFactory() {
        return true;
    }
}
