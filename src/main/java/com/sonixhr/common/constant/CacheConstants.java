package com.sonixhr.common.constant;

import java.time.Duration;

public final class CacheConstants {

    private CacheConstants() {}

    // ── Cache names ──────────────────────────────────────────────────────────
    public static final String CACHE_CALENDAR    = "calendar";
    public static final String CACHE_EMPLOYEES   = "employees";
    public static final String CACHE_ATTENDANCE  = "attendance";
    public static final String CACHE_AUTHORITIES = "tenant_user_authorities";

    // ── TTLs ─────────────────────────────────────────────────────────────────
    public static final Duration TTL_EMPLOYEES      = Duration.ofMinutes(10);
    public static final Duration TTL_ATTENDANCE     = Duration.ofMinutes(10);
    public static final Duration TTL_CALENDAR       = Duration.ofMinutes(5);
    public static final Duration TTL_ROLES          = Duration.ofMinutes(30);
    public static final Duration TTL_PERMISSIONS    = Duration.ofMinutes(60);
    public static final Duration TTL_AUTHORITIES    = Duration.ofMinutes(5);
    public static final Duration TTL_LOGIN_ATTEMPTS = Duration.ofMinutes(30);
    public static final Duration TTL_JWT            = Duration.ofMinutes(15);
    public static final Duration TTL_TENANT         = Duration.ofMinutes(60);
    public static final Duration TTL_DEPARTMENTS    = Duration.ofMinutes(60);

    // ── Cache max sizes ───────────────────────────────────────────────────────
    public static final int MAX_SIZE_EMPLOYEES   = 1000;
    public static final int MAX_SIZE_CALENDAR    = 5000;
    public static final int MAX_SIZE_ROLES       = 500;
    public static final int MAX_SIZE_PERMISSIONS = 500;
    public static final int MAX_SIZE_AUTHORITIES = 2000;

    // ── Redis key prefixes ────────────────────────────────────────────────────
    public static final String REDIS_PREFIX       = "sonixhr:";
    public static final String REDIS_PREFIX_USER  = REDIS_PREFIX + "user:";
    public static final String REDIS_PREFIX_ROLE  = REDIS_PREFIX + "role:";
    public static final String REDIS_PREFIX_TOKEN = REDIS_PREFIX + "token:";
}
