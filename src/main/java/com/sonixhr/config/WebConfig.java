package com.sonixhr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Trailing-slash matching removed: deprecated in Spring 6 and removed in Spring 6.x.
    // Normalize trailing slashes at the frontend/gateway level, or add explicit routes.
}
