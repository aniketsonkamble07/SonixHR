package com.sonixhr.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMonitoringService {

    private final CacheManager cacheManager;

    private final Map<String, CacheStats> cacheStats = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60000) // Every minute
    public void logCacheStats() {
        if (cacheManager instanceof CaffeineCacheManager caffeineCacheManager) {
            caffeineCacheManager.getCacheNames().forEach(cacheName -> {
                Cache cache = caffeineCacheManager.getCache(cacheName);
                if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache nativeCache) {
                    com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();

                    CacheStats currentStats = cacheStats.computeIfAbsent(cacheName, k -> new CacheStats());
                    currentStats.hitCount = stats.hitCount();
                    currentStats.missCount = stats.missCount();
                    currentStats.loadSuccessCount = stats.loadSuccessCount();
                    currentStats.loadFailureCount = stats.loadFailureCount();
                    currentStats.totalLoadTime = stats.totalLoadTime();

                    double hitRate = stats.hitRate();
                    double missRate = stats.missRate();

                    log.info("Cache '{}' - Hit Rate: {:.2f}%, Miss Rate: {:.2f}%, Size: {}",
                            cacheName, hitRate * 100, missRate * 100,
                            nativeCache.estimatedSize());

                    // Alert if hit rate is too low
                    if (hitRate < 0.5) {
                        log.warn("Cache '{}' has low hit rate: {:.2f}%", cacheName, hitRate * 100);
                    }
                }
            });
        }
    }

    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void logRedisCacheStats() {
        if (cacheManager instanceof RedisCacheManager redisCacheManager) {
            redisCacheManager.getCacheNames().forEach(cacheName -> {
                // Redis stats can be obtained via RedisTemplate or Actuator
                log.debug("Redis cache '{}' available", cacheName);
            });
        }
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        });
    }

    public Map<String, CacheStats> getCacheStats() {
        return cacheStats;
    }

    static class CacheStats {
        long hitCount = 0;
        long missCount = 0;
        long loadSuccessCount = 0;
        long loadFailureCount = 0;
        long totalLoadTime = 0;

        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0 : (double) hitCount / total;
        }
    }
}