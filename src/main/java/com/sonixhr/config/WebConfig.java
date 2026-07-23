package com.sonixhr.config;

import com.sonixhr.security.ApiHitLogInterceptor;
import com.sonixhr.security.TenantExpirationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @NonNull
    private final ApiHitLogInterceptor apiHitLogInterceptor;

    @NonNull
    private final TenantExpirationInterceptor tenantExpirationInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // API logging interceptor - applies to all API paths
        registry.addInterceptor(apiHitLogInterceptor)
                .addPathPatterns("/api/**");

        // Tenant expiration interceptor - applies to tenant APIs only
        registry.addInterceptor(tenantExpirationInterceptor)
                .addPathPatterns("/api/tenant/**", "/api/tenants/**")
                .excludePathPatterns(
                        "/api/tenant/auth/activate",
                        "/api/tenant/auth/forgot-password",
                        "/api/tenant/auth/reset-password",
                        "/api/tenants/register",
                        "/api/public/**",
                        "/actuator/**",
                        "/api/health/**"
                );
    }
}