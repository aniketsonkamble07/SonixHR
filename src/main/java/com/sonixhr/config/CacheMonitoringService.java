package com.sonixhr.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs L1 (Caffeine) hit/miss stats every minute and L2 (Redis) availability
 * every 5 minutes.
 *
 * The primary CacheManager is a CompositeCacheManager, so the individual
 * caffeine/redis beans are injected by qualifier to avoid instanceof checks
 * that would always fail against the composite.
 */
@Slf4j
@Component
public class CacheMonitoringService {

    private final CaffeineCacheManager caffeineCacheManager;
    private final RedisCacheManager redisCacheManager;
    private final Map<String, CacheStats> cacheStats = new ConcurrentHashMap<>();

    public CacheMonitoringService(
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Qualifier("redisCacheManager") CacheManager redisCacheManager) {
        this.caffeineCacheManager = (CaffeineCacheManager) caffeineCacheManager;
        this.redisCacheManager    = (RedisCacheManager) redisCacheManager;
    }

    @Scheduled(fixedDelay = 60_000)  // every minute
    public void logCacheStats() {
        caffeineCacheManager.getCacheNames().forEach(cacheName -> {
            if (cacheName == null) return;
            Cache cache = caffeineCacheManager.getCache(cacheName);
            if (cache == null) return;

            Object native0 = cache.getNativeCache();
            if (!(native0 instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache)) return;

            com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();

            CacheStats current = cacheStats.computeIfAbsent(cacheName, k -> new CacheStats());
            current.hitCount         = stats.hitCount();
            current.missCount        = stats.missCount();
            current.loadSuccessCount = stats.loadSuccessCount();
            current.loadFailureCount = stats.loadFailureCount();
            current.totalLoadTime    = stats.totalLoadTime();

            long requestCount = stats.requestCount();
            double hitRate  = requestCount == 0 ? 0.0 : stats.hitRate();
            double missRate = requestCount == 0 ? 0.0 : stats.missRate();
            long   size     = nativeCache.estimatedSize();

            // SLF4J uses {} placeholders, not {:.2f}
            log.info("L1 cache '{}' — hitRate={}% missRate={}% size={}",
                    cacheName,
                    Math.round(hitRate * 1000) / 10.0,
                    Math.round(missRate * 1000) / 10.0,
                    size);

            if (hitRate < 0.5 && (current.hitCount + current.missCount) > 10) {
                log.warn("L1 cache '{}' has low hit rate: {}%", cacheName,
                        Math.round(hitRate * 1000) / 10.0);
            }
        });
    }

    @Scheduled(fixedDelay = 300_000)  // every 5 minutes
    public void logRedisCacheStats() {
        long count = redisCacheManager.getCacheNames().stream()
                .filter(name -> name != null && redisCacheManager.getCache(name) != null)
                .count();
        log.debug("L2 Redis: {} named caches available", count);
    }

    /** Emergency: wipe every cache. Use sparingly. */
    public void evictAllCaches() {
        caffeineCacheManager.getCacheNames().forEach(name -> {
            if (name == null) return;
            Cache c = caffeineCacheManager.getCache(name);
            if (c != null) { c.clear(); log.info("Cleared L1 cache: {}", name); }
        });
        redisCacheManager.getCacheNames().forEach(name -> {
            if (name == null) return;
            Cache c = redisCacheManager.getCache(name);
            if (c != null) { c.clear(); log.info("Cleared L2 cache: {}", name); }
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

        public long   getHitCount()  { return hitCount; }
        public long   getMissCount() { return missCount; }
        public double getHitRate()   {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
}
