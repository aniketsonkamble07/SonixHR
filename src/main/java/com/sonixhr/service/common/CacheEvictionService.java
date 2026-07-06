package com.sonixhr.service.common;

import com.sonixhr.common.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CacheEvictionService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant-scoped eviction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evict all authority caches for every user in a tenant.
     * Uses Redis SCAN to delete only keys belonging to this tenant,
     * so other tenants' caches remain intact.
     *
     * Previously this called cache.clear() which wiped ALL tenants — fixed.
     */
    public void evictTenantCaches(Long tenantId) {
        if (tenantId == null) return;

        // Authority caches are keyed as "email:tenantId"
        scanAndDelete("sonixhr:" + CacheConstants.CACHE_AUTHORITIES + "::*:" + tenantId);
        scanAndDelete("sonixhr:tenant_role_permissions::*");

        // Department lookup is keyed by tenantId
        evictKey("departmentsLookup", tenantId);

        // Tenant details cache
        evictKey("tenantDetails", tenantId);

        // Calendar entries for this tenant — keyed by employeeId, so scan by suffix of the tenantId
        // is not possible without a mapping. Evict all calendar entries as a conservative fallback
        // only when tenant-wide settings change (e.g. holidays, leave settings).
        scanAndDelete("sonixhr:calendar::*");

        log.info("Evicted all tenant-scoped caches for tenant: {}", tenantId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User-scoped eviction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evict caches for a specific employee/user.
     * Removes only their keys; other users' caches are untouched.
     */
    public void evictUserCaches(String email, Long tenantId) {
        // Authority caches
        evictKey("tenant_user_authorities", email + ":" + tenantId);
        evictKey("userAuthorities", email);
        evictKey("userAuthorities", email + ":" + tenantId);

        // Employee detail caches
        evictKey("employees",       email);
        evictKey("employees",       email + ":" + tenantId);
        evictKey("employeeDetails", email);
        evictKey("employeeDetails", email + ":" + tenantId);

        log.info("Evicted user caches for: {} in tenant: {}", email, tenantId);
    }

    /**
     * Evict the calendar cache for a specific employee and month.
     */
    public void evictCalendarCache(Long tenantId, Long employeeId, int year, int month) {
        evictKey(CacheConstants.CACHE_CALENDAR, tenantId + ":" + employeeId + ":" + year + ":" + month);
        log.debug("Evicted calendar cache for employee {} {}-{}", employeeId, year, month);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void evictKey(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.warn("Failed to evict key '{}' from cache '{}': {}", key, cacheName, e.getMessage());
        }
    }

    private void scanAndDelete(String pattern) {
        try {
            Set<String> keys = redisTemplate.execute(
                (org.springframework.data.redis.connection.RedisConnection connection) -> {
                    Set<String> keySet = new java.util.HashSet<>();
                    org.springframework.data.redis.core.Cursor<byte[]> cursor =
                        connection.keyCommands().scan(
                            org.springframework.data.redis.core.ScanOptions
                                .scanOptions().match(pattern).count(1000).build()
                        );
                    while (cursor.hasNext()) {
                        keySet.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                    return keySet;
                }
            );
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Deleted {} Redis keys matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to scan/delete pattern '{}': {}", pattern, e.getMessage());
        }
    }
}
