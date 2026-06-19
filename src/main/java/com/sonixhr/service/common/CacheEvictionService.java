package com.sonixhr.service.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CacheEvictionService {

    private final CacheManager cacheManager;

    /**
     * Evict all caches for a specific tenant
     */
    public void evictTenantCaches(Long tenantId) {

        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                // Since Redis doesn't support pattern deletion directly,
                // we'll clear the entire cache or implement custom logic
                log.info("Clearing cache '{}' for tenant: {}", cacheName, tenantId);
                cache.clear();  // Or use more granular eviction
            }
        });
    }

    /**
     * Evict user-specific caches
     */
    public void evictUserCaches(String email, Long tenantId) {
        String[] cacheNames = {"employees", "userAuthorities", "employeeDetails"};

        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                // Use specific key if possible, otherwise clear
                cache.evict(email);
                cache.evict(email + ":" + tenantId);
                log.info("Evicted {} cache for user: {}", cacheName, email);
            }
        }
    }
}