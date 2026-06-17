package com.sonixhr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
 
@Configuration
@SuppressWarnings("deprecation")
public class WebConfig implements WebMvcConfigurer {
 
    @Override
    public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
        // Enable trailing slash matching (e.g. /api/tenant/departments/ matches /api/tenant/departments)
        configurer.setUseTrailingSlashMatch(true);
    }
}
