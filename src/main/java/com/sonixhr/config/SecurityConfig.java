package com.sonixhr.config;

import com.sonixhr.security.CustomPermissionEvaluator;
import com.sonixhr.security.JwtAccessDeniedHandler;
import com.sonixhr.security.JwtAuthenticationEntryPoint;
import com.sonixhr.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@SuppressWarnings("null")
public class SecurityConfig {

    // ✅ NO DEFAULTS - MUST be defined in application.properties
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${app.cors.allowed-methods}")
    private List<String> allowedMethods;

    @Value("${app.cors.allowed-headers}")
    private List<String> allowedHeaders;

    @Value("${app.cors.exposed-headers}")
    private List<String> exposedHeaders;

    @Value("${app.cors.max-age}")
    private long maxAge;

    private final UserDetailsService employeeDetailsService;
    private final UserDetailsService platformUserDetailsService;
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint unauthorizedHandler;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final CustomPermissionEvaluator permissionEvaluator;

    public SecurityConfig(
            @Qualifier("employeeDetailsService") UserDetailsService employeeDetailsService,
            @Qualifier("platformUserDetailsService") UserDetailsService platformUserDetailsService,
            JwtAuthFilter jwtAuthFilter,
            JwtAuthenticationEntryPoint unauthorizedHandler,
            JwtAccessDeniedHandler accessDeniedHandler,
            CustomPermissionEvaluator permissionEvaluator) {
        this.employeeDetailsService = employeeDetailsService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.unauthorizedHandler = unauthorizedHandler;
        this.accessDeniedHandler = accessDeniedHandler;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/**", "/v3/api-docs/**", "/api-docs/**", "/swagger-ui/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // =====================================================
                        // PUBLIC ENDPOINTS - No authentication required
                        // =====================================================
                        .requestMatchers(
                                // Public auth endpoints
                                "/api/auth/**",

                                // Platform auth - PUBLIC ONLY
                                "/api/platform/auth/login",
                                "/api/platform/auth/activate",
                                "/api/platform/auth/forgot-password",
                                "/api/platform/auth/reset-password",
                                "/api/platform/auth/resend-activation",
                                "/api/platform/auth/refresh",
                                "/api/platform/auth/verify-token",

                                // Tenant auth - PUBLIC ONLY
                                "/api/tenant/auth/login",
                                "/api/tenant/auth/activate",
                                "/api/tenant/auth/forgot-password",
                                "/api/tenant/auth/reset-password",
                                "/api/tenant/auth/resend-activation",
                                "/api/tenant/auth/refresh",

                                // Employee activation
                                "/api/employee/auth/activate",

                                // Public endpoints
                                "/api/public/**",
                                "/api/tenant/register",
                                "/api/forgot-password/**",
                                "/api/reset-password/**",

                                // Public plan endpoints
                                "/api/platform/subscription-plans/public",

                                // Health and monitoring
                                "/api/health",
                                "/actuator/health",
                                "/actuator/**",

                                // Swagger/OpenAPI
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",

                                // Static resources
                                "/",
                                "/error",
                                "/api/debug/**",
                                "/tenant/**",
                                "/platform/**",
                                "/*.html",
                                "/*.css",
                                "/*.js",
                                "/favicon.ico")
                        .permitAll()

                        // =====================================================
                        // BILLING AND SUBSCRIPTION ENDPOINTS
                        // =====================================================
                        .requestMatchers(
                                "/api/tenant/billing/**",
                                "/api/tenant/subscriptions/**",
                                "/api/tenant/subscription/**",
                                "/api/tenant/subscription/history/**",
                                "/api/platform/subscription-plans",
                                "/api/platform/subscription-plans/{id}",
                                "/api/platform/subscription-plans/code/{code}",
                                "/api/platform/subscription-plans/{id}/history")
                        .permitAll()

                        // =====================================================
                        // PLATFORM ADMIN - Require Platform Permissions
                        // =====================================================
                        .requestMatchers("/api/platform/admin/**")
                        .hasAnyAuthority(Arrays.stream(com.sonixhr.enums.PlatformPermissionEnum.values())
                                .map(Enum::name)
                                .toArray(String[]::new))

                        // =====================================================
                        // AUTHENTICATED ENDPOINTS - Require valid JWT
                        // =====================================================
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ========================
    // TENANT / EMPLOYEE AUTHENTICATION
    // ========================
    @Bean(name = "tenantAuthenticationManager")
    public AuthenticationManager tenantAuthenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(employeeDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(List.of(provider));
    }

    // ========================
    // PLATFORM AUTHENTICATION
    // ========================
    @Bean(name = "platformAuthenticationManager")
    public AuthenticationManager platformAuthenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(platformUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(List.of(provider));
    }

    // ========================
    // DEFAULT AUTHENTICATION MANAGER
    // ========================
    @Primary
    @Bean
    public AuthenticationManager authenticationManager() {
        return platformAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public MethodSecurityExpressionHandler expressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}