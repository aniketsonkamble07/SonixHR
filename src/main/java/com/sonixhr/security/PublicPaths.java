package com.sonixhr.security;

import java.util.List;
import java.util.Set;

/**
 * Single source of truth for public (unauthenticated) API paths.
 * Referenced by both JwtAuthFilter and TenantContextFilter to prevent drift.
 */
public final class PublicPaths {

    private PublicPaths() {}

    /** Exact-match paths that require no authentication. */
    public static final Set<String> EXACT = Set.of(
            "/api/health",
            "/actuator/health",
            "/api/tenants/register",
            "/error"
    );

    /**
     * Prefix patterns — any path starting with one of these is public.
     * ⚠️ CRITICAL: Do NOT use wildcard prefixes for /api/tenant/auth/ or /api/platform/auth/
     * as they would make ALL auth endpoints public including change-password.
     * Instead, list individual public endpoints.
     */
    public static final List<String> PREFIXES = List.of(
            // General auth (if any)
            "/api/auth/",

            // Platform auth - INDIVIDUAL PUBLIC ENDPOINTS ONLY
            "/api/platform/auth/login",
            "/api/platform/auth/activate",
            "/api/platform/auth/forgot-password",
            "/api/platform/auth/reset-password",
            "/api/platform/auth/resend-activation",
            "/api/platform/auth/refresh",
            "/api/platform/auth/verify-token",

            // Tenant auth - INDIVIDUAL PUBLIC ENDPOINTS ONLY
            "/api/tenant/auth/login",
            "/api/tenant/auth/activate",
            "/api/tenant/auth/forgot-password",
            "/api/tenant/auth/reset-password",
            "/api/tenant/auth/resend-activation",
            "/api/tenant/auth/refresh",

            // ❌ DO NOT add these as prefixes:
            // "/api/tenant/auth/" - would make ALL tenant auth endpoints public
            // "/api/platform/auth/" - would make ALL platform auth endpoints public

            // Employee activation
            "/api/employee/auth/activate",

            // Public endpoints
            "/api/public/",
            "/api/forgot-password/",
            "/api/reset-password/",
            "/api/platform/subscription-plans/public",

            // Swagger/OpenAPI
            "/swagger-ui/",
            "/v3/api-docs/",
            "/api-docs/",

            // Debug
            "/api/debug/",

            // Actuator
            "/actuator/"
    );

    /**
     * Returns true if the path is unconditionally public.
     */
    public static boolean isPublic(String path) {
        if (path == null) return false;
        if (EXACT.contains(path)) return true;
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Check if a path is authenticated (not public)
     */
    public static boolean isAuthenticated(String path) {
        return !isPublic(path);
    }
}