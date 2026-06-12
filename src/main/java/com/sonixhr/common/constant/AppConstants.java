package com.sonixhr.common.constant;

public final class AppConstants {

    private AppConstants() {}

    // Cache Names
    public static final String CACHE_EMPLOYEES = "employees";
    public static final String CACHE_TENANT_ROLES = "tenantRoles";
    public static final String CACHE_PLATFORM_ROLES = "platformRoles";
    public static final String CACHE_PERMISSIONS = "permissions";
    public static final String CACHE_USER_AUTHORITIES = "userAuthorities";
    public static final String CACHE_LOGIN_ATTEMPTS = "loginAttempts";
    public static final String CACHE_LOCKED_ACCOUNTS = "lockedAccounts";

    // User Types
    public static final String USER_TYPE_PLATFORM = "PLATFORM";
    public static final String USER_TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String USER_TYPE_TENANT = "TENANT";

    // Token Types
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    // Default Values
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    // API Paths
    public static final String API_PREFIX = "/api";
    public static final String API_PLATFORM = API_PREFIX + "/platform";
    public static final String API_TENANT = API_PREFIX + "/tenant";
    public static final String API_PUBLIC = API_PREFIX + "/public";
    public static final String API_AUTH = API_PREFIX + "/auth";
}
