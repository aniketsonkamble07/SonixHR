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
     * Authenticated sub-paths (e.g. /api/tenant/auth/me) are explicitly
     * excluded in JwtAuthFilter.isPublicPathOptimized().
     */
    public static final List<String> PREFIXES = List.of(
            "/api/auth/",
            "/api/platform/auth/",
            "/api/tenant/auth/",
            "/api/public/",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/api-docs/",
            "/api/debug/",
            "/api/forgot-password/",
            "/api/reset-password/",
            "/actuator/"
    );

    /** Returns true if the path is unconditionally public. */
    public static boolean isPublic(String path) {
        if (path == null) return false;
        if (EXACT.contains(path)) return true;
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
