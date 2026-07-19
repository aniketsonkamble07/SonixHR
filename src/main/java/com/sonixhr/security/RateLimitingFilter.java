package com.sonixhr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Applies IP-based rate limiting to authentication endpoints.
 *
 * Uses {@link RateLimiterService} (Redis-backed) so limits are enforced
 * correctly across multiple app instances. Reads limits from
 * {@code app.rate-limit.*} properties defined per environment.
 *
 * Guarded paths (prefix match):
 *   /api/auth/**, /api/platform/auth/**, /api/tenant/auth/**,
 *   /api/forgot-password/**, /api/reset-password/**
 *
 * IP resolution honours X-Forwarded-For so deployments behind a reverse
 * proxy or load balancer (e.g. Render, Nginx) see the real client IP.
 */
@Slf4j
@Component
@Order(1)  // Run before JwtAuthFilter
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.login-attempts:5}")
    private int loginAttempts;

    @Value("${app.rate-limit.login-window-minutes:15}")
    private long loginWindowMinutes;

    private static final String[] GUARDED_PREFIXES = {
            "/api/auth/",
            "/api/platform/auth/",
            "/api/tenant/auth/",
            "/api/forgot-password/",
            "/api/reset-password/"
    };

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (rateLimitEnabled && isGuardedPath(request.getRequestURI())) {
            String ip = resolveClientIp(request);
            try {
                rateLimiterService.checkOrThrow(
                        "login:ip:" + ip,
                        loginAttempts,
                        loginWindowMinutes * 60
                );
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                    return;
                }
                throw new ServletException(e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isGuardedPath(String uri) {
        for (String prefix : GUARDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the real client IP, honouring X-Forwarded-For for reverse-proxy deployments.
     * Only the first (leftmost) IP in the header is used, as that is the original client.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
