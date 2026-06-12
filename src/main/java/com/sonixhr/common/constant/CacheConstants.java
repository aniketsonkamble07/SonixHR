package com.sonixhr.common.constant;

import java.time.Duration;

public final class CacheConstants {

    private CacheConstants() {}

    // Cache TTLs in minutes
    public static final Duration TTL_EMPLOYEES = Duration.ofMinutes(10);
    public static final Duration TTL_ROLES = Duration.ofMinutes(30);
    public static final Duration TTL_PERMISSIONS = Duration.ofMinutes(60);
    public static final Duration TTL_AUTHORITIES = Duration.ofMinutes(5);
    public static final Duration TTL_LOGIN_ATTEMPTS = Duration.ofMinutes(30);
    public static final Duration TTL_JWT = Duration.ofMinutes(15);

    // Cache maximum sizes
    public static final int MAX_SIZE_EMPLOYEES = 1000;
    public static final int MAX_SIZE_ROLES = 500;
    public static final int MAX_SIZE_PERMISSIONS = 500;
    public static final int MAX_SIZE_AUTHORITIES = 2000;

    // Redis key prefixes
    public static final String REDIS_PREFIX = "sonixhr:";
    public static final String REDIS_PREFIX_USER = REDIS_PREFIX + "user:";
    public static final String REDIS_PREFIX_ROLE = REDIS_PREFIX + "role:";
    public static final String REDIS_PREFIX_TOKEN = REDIS_PREFIX + "token:";
}