package com.sonixhr.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
 
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"unused", "null"})
public class PlatformTokenBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtService jwtService;

    @Value("${app.token.blacklist.enabled:true}")
    private boolean blacklistEnabled;

    @Value("${app.token.blacklist.redis-enabled:true}")
    private boolean redisEnabled;

    @Value("${app.token.blacklist.cache-size:10000}")
    private int localCacheSize;

    @Value("${app.token.blacklist.cleanup-interval:3600000}")
    private long cleanupInterval;

    // Redis cache keys
    private static final String REDIS_KEY_BLACKLIST = "token:blacklist:";
    private static final String REDIS_KEY_USER_BLACKLIST = "token:user-blacklist:";
    private static final String REDIS_KEY_TOKEN_INFO = "token:info:";

    // Performance optimization: Cache TTL for faster access
    private static final int POSITIVE_CACHE_TTL_SECONDS = 300; // 5 minutes

    // Local in-memory fallback (used when Redis is unavailable)
    private final Map<String, BlacklistEntry> localBlacklist = new ConcurrentHashMap<>();

    // Cache for frequently checked tokens (positive cache) - Enhanced with TTL and max size
    private Cache<String, CachedBlacklistResult> positiveCache;

    @PostConstruct
    private void initCache() {
        positiveCache = Caffeine.newBuilder()
                .expireAfterWrite(POSITIVE_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(localCacheSize)
                .build();
    }

    // Statistics for monitoring
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong redisHits = new AtomicLong(0);
    private final AtomicLong localHits = new AtomicLong(0);

    private static class BlacklistEntry {
        final long expiryTime;
        final String jti;
        final String username;
        final String userType;
        final long blacklistedAt;

        BlacklistEntry(String jti, String username, String userType, long expiryTime) {
            this.jti = jti;
            this.username = username;
            this.userType = userType;
            this.expiryTime = expiryTime;
            this.blacklistedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return expiryTime < System.currentTimeMillis();
        }

        long getRemainingTime() {
            return Math.max(0, expiryTime - System.currentTimeMillis());
        }
    }

    private static class CachedBlacklistResult {
        final boolean isBlacklisted;
        final long cachedAt;
        final long expiresAt;

        CachedBlacklistResult(boolean isBlacklisted) {
            this.isBlacklisted = isBlacklisted;
            this.cachedAt = System.currentTimeMillis();
            this.expiresAt = this.cachedAt + TimeUnit.SECONDS.toMillis(POSITIVE_CACHE_TTL_SECONDS);
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    /**
     * Blacklist a token with its actual expiration time from JWT - Optimized
     */
    public void blacklistToken(String token) {
        if (!blacklistEnabled) {
            log.debug("Token blacklist is disabled");
            return;
        }

        long startTime = System.nanoTime();

        try {
            // Extract token information once
            Claims claims = jwtService.extractAllClaims(token);
            String jti = claims.getId();
            String username = claims.getSubject();
            String userType = jwtService.extractUserType(token);
            Date expiration = claims.getExpiration();

            long ttl = expiration.getTime() - System.currentTimeMillis();

            if (ttl <= 0) {
                log.debug("Token already expired, no need to blacklist: {}", jti);
                return;
            }

            // Add to Redis blacklist (primary)
            boolean redisSuccess = false;
            if (redisEnabled && redisTemplate != null) {
                redisSuccess = blacklistTokenInRedis(jti, username, userType, ttl);
            }

            // Fallback to local if Redis fails
            if (!redisSuccess) {
                blacklistTokenLocally(jti, username, userType, ttl);
            }

            // Also blacklist by user for batch operations
            if (username != null && userType != null) {
                addToUserBlacklist(username, userType, jti, ttl);
            }

            // Cache the positive result with TTL
            positiveCache.put(token, new CachedBlacklistResult(true));

            // Also cache by JTI for faster lookups
            positiveCache.put("jti:" + jti, new CachedBlacklistResult(true));

            long duration = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);
            log.info("Token blacklisted - JTI: {}, User: {}, TTL: {}ms, Duration: {}μs",
                    jti, username, ttl, duration);

        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
        }
    }

    /**
     * Blacklist token in Redis with TTL - Fixed ambiguous method call
     * Using simple opsForHash (recommended for clarity)
     */
    private boolean blacklistTokenInRedis(String jti, String username, String userType, long ttl) {
        try {
            String key = REDIS_KEY_BLACKLIST + jti;

            // Simple approach - no ambiguity
            Map<String, String> hashMap = new HashMap<>();
            hashMap.put("jti", jti);
            hashMap.put("username", username != null ? username : "");
            hashMap.put("userType", userType != null ? userType : "");
            hashMap.put("blacklistedAt", String.valueOf(System.currentTimeMillis()));

            redisTemplate.opsForHash().putAll(key, hashMap);
            redisTemplate.expire(key, ttl, TimeUnit.MILLISECONDS);

            log.debug("Token blacklisted in Redis: {}, TTL: {}ms", jti, ttl);
            return true;
        } catch (Exception e) {
            log.warn("Failed to blacklist token in Redis: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Blacklist token locally (in-memory fallback)
     */
    private void blacklistTokenLocally(String jti, String username, String userType, long ttl) {
        localBlacklist.put(jti, new BlacklistEntry(jti, username, userType, System.currentTimeMillis() + ttl));

        // Clean up local cache if it exceeds size limit
        if (localBlacklist.size() > localCacheSize) {
            cleanupLocalBlacklist();
        }

        log.debug("Token blacklisted locally: {}, TTL: {}ms", jti, ttl);
    }

    /**
     * Add token to user's blacklist collection for batch operations
     */
    private void addToUserBlacklist(String username, String userType, String jti, long ttl) {
        if (!redisEnabled || redisTemplate == null) {
            return;
        }

        try {
            String userKey = REDIS_KEY_USER_BLACKLIST + userType + ":" + username;
            redisTemplate.opsForSet().add(userKey, jti);
            redisTemplate.expire(userKey, ttl, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Failed to add to user blacklist: {}", e.getMessage());
        }
    }

    /**
     * Check if a token is blacklisted - Optimized with multi-level cache
     */
    public boolean isBlacklisted(String token) {
        if (!blacklistEnabled) {
            return false;
        }

        long startTime = System.nanoTime();

        // Level 1: Check positive cache (fastest)
        CachedBlacklistResult cached = positiveCache.getIfPresent(token);
        if (cached != null && cached.isValid()) {
            cacheHits.incrementAndGet();
            return cached.isBlacklisted;
        }

        // Also check by JTI if we have it cached
        String jti = null;
        try {
            jti = jwtService.extractJti(token);
            if (jti != null) {
                CachedBlacklistResult jtiCached = positiveCache.getIfPresent("jti:" + jti);
                if (jtiCached != null && jtiCached.isValid()) {
                    cacheHits.incrementAndGet();
                    positiveCache.put(token, jtiCached); // Cache by token too
                    return jtiCached.isBlacklisted;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract JTI for cache check: {}", e.getMessage());
        }

        cacheMisses.incrementAndGet();

        if (jti == null) {
            return false;
        }

        boolean isBlacklisted = false;

        // Level 2: Check Redis (distributed)
        if (redisEnabled && redisTemplate != null) {
            isBlacklisted = isBlacklistedInRedis(jti);
            if (isBlacklisted) {
                redisHits.incrementAndGet();
            }
        }

        // Level 3: Fallback to local blacklist
        if (!isBlacklisted) {
            isBlacklisted = isBlacklistedLocally(jti);
            if (isBlacklisted) {
                localHits.incrementAndGet();
            }
        }

        // Cache the result with TTL
        if (isBlacklisted) {
            positiveCache.put(token, new CachedBlacklistResult(true));
            if (jti != null) {
                positiveCache.put("jti:" + jti, new CachedBlacklistResult(true));
            }
        }

        long duration = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);
        if (duration > 100) { // Log only slow operations
            log.debug("Blacklist check took {}μs, result: {}", duration, isBlacklisted);
        }

        return isBlacklisted;
    }

    /**
     * Check if token is blacklisted in Redis
     */
    private boolean isBlacklistedInRedis(String jti) {
        try {
            String key = REDIS_KEY_BLACKLIST + jti;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.debug("Redis blacklist check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is blacklisted locally
     */
    private boolean isBlacklistedLocally(String jti) {
        BlacklistEntry entry = localBlacklist.get(jti);
        if (entry == null) {
            return false;
        }

        if (entry.isExpired()) {
            localBlacklist.remove(jti);
            return false;
        }

        return true;
    }

    /**
     * Blacklist all tokens for a specific user
     */
    public void blacklistAllUserTokens(String username, String userType) {
        if (!blacklistEnabled) {
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            if (redisEnabled && redisTemplate != null) {
                String userKey = REDIS_KEY_USER_BLACKLIST + userType + ":" + username;
                Set<Object> tokenJtis = redisTemplate.opsForSet().members(userKey);

                if (tokenJtis != null && !tokenJtis.isEmpty()) {
                    // Get remaining TTL of the user's blacklist set as a baseline
                    Long userKeyTtl = redisTemplate.getExpire(userKey, TimeUnit.MILLISECONDS);
                    long ttlToUse = (userKeyTtl != null && userKeyTtl > 0) ? userKeyTtl : jwtService.getRefreshExpiration();

                    // Batch process token blacklisting
                    for (Object jtiObj : tokenJtis) {
                        String jti = jtiObj.toString();
                        String key = REDIS_KEY_BLACKLIST + jti;

                        // Use setIfAbsent to avoid overwriting existing TTL
                        Boolean exists = redisTemplate.hasKey(key);
                        if (Boolean.FALSE.equals(exists)) {
                            Map<String, String> hashMap = new HashMap<>();
                            hashMap.put("jti", jti);
                            hashMap.put("username", username);
                            hashMap.put("userType", userType);
                            hashMap.put("blacklistedAt", String.valueOf(System.currentTimeMillis()));
                            redisTemplate.opsForHash().putAll(key, hashMap);
                            redisTemplate.expire(key, ttlToUse, TimeUnit.MILLISECONDS);
                        }

                        // Cache by JTI
                        positiveCache.put("jti:" + jti, new CachedBlacklistResult(true));
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Blacklisted {} tokens for user: {} ({}) in {}ms",
                            tokenJtis.size(), username, userType, duration);
                }
            }

            // Clear positive cache entries for this user
            clearPositiveCacheForUser(username);

        } catch (Exception e) {
            log.error("Failed to blacklist user tokens: {}", e.getMessage());
        }
    }

    /**
     * Blacklist all tokens for a specific tenant
     */
    public void blacklistAllTenantTokens(Long tenantId) {
        if (!blacklistEnabled || tenantId == null) {
            return;
        }

        log.info("Blacklisting all tokens for tenant: {}", tenantId);
        try {
            if (redisEnabled && redisTemplate != null) {
                String key = "tenant:blacklist:" + tenantId;
                redisTemplate.opsForValue().set(key, "true", 7, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("Failed to blacklist tenant tokens in Redis: {}", e.getMessage());
        }
    }

    /**
     * Remove a token from blacklist (for testing or admin override)
     */
    public void removeFromBlacklist(String token) {
        try {
            String jti = jwtService.extractJti(token);

            if (redisEnabled && redisTemplate != null) {
                String key = REDIS_KEY_BLACKLIST + jti;
                redisTemplate.delete(key);
            }

            localBlacklist.remove(jti);
            positiveCache.invalidate(token);
            if (jti != null) {
                positiveCache.invalidate("jti:" + jti);
            }

            log.info("Token removed from blacklist: {}", jti);
        } catch (Exception e) {
            log.error("Failed to remove token from blacklist: {}", e.getMessage());
        }
    }

    /**
     * Get blacklist statistics - Enhanced with hit rates
     */
    public BlacklistStats getStats() {
        BlacklistStats stats = new BlacklistStats();

        if (redisEnabled && redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys(REDIS_KEY_BLACKLIST + "*");
                stats.redisSize = keys != null ? keys.size() : 0;
            } catch (Exception e) {
                log.debug("Failed to get Redis stats: {}", e.getMessage());
            }
        }

        stats.localSize = localBlacklist.size();
        stats.positiveCacheSize = (int) positiveCache.estimatedSize();
        stats.cacheHits = cacheHits.get();
        stats.cacheMisses = cacheMisses.get();
        stats.redisHits = redisHits.get();
        stats.localHits = localHits.get();

        long totalChecks = stats.cacheHits + stats.cacheMisses;
        stats.hitRate = totalChecks > 0 ? (double) stats.cacheHits / totalChecks * 100 : 0;

        return stats;
    }

    /**
     * Clean up expired entries from local blacklist
     */
    @Scheduled(fixedDelayString = "${app.token.blacklist.cleanup-interval:3600000}")
    public void cleanupLocalBlacklist() {
        int beforeSize = localBlacklist.size();

        // Batch remove expired entries
        localBlacklist.entrySet().removeIf(entry -> entry.getValue().isExpired());

        int afterSize = localBlacklist.size();

        // Clean up positive cache (remove expired entries)
        positiveCache.cleanUp();

        if (beforeSize != afterSize) {
            log.debug("Local blacklist cleaned: removed {} expired entries, cache size: {}",
                    beforeSize - afterSize, positiveCache.estimatedSize());
        }
    }

    /**
     * Clear positive cache for a specific user
     */
    private void clearPositiveCacheForUser(String username) {
        if (username == null) return;
        positiveCache.asMap().keySet().removeIf(key -> {
            try {
                if (key.startsWith("jti:")) {
                    return false;
                }
                String tokenUsername = jwtService.extractUsername(key);
                return username.equalsIgnoreCase(tokenUsername);
            } catch (Exception e) {
                return false;
            }
        });
        log.debug("Cleared positive cache for user: {}", username);
    }

    /**
     * Get remaining TTL for a blacklisted token
     */
    public long getRemainingTTL(String token) {
        try {
            String jti = jwtService.extractJti(token);

            if (redisEnabled && redisTemplate != null) {
                String key = REDIS_KEY_BLACKLIST + jti;
                Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
                if (ttl != null && ttl > 0) {
                    return ttl;
                }
            }

            BlacklistEntry entry = localBlacklist.get(jti);
            if (entry != null && !entry.isExpired()) {
                return entry.getRemainingTime();
            }

        } catch (Exception e) {
            log.debug("Failed to get TTL: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * Check if blacklist is healthy
     */
    public boolean isHealthy() {
        if (redisEnabled && redisTemplate != null) {
            try {
                Boolean isPong = redisTemplate.execute((RedisCallback<Boolean>) connection -> 
                        "PONG".equals(connection.ping())
                );
                return Boolean.TRUE.equals(isPong);
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Get cache hit rate percentage
     */
    public double getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (double) cacheHits.get() / total * 100 : 0;
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
        redisHits.set(0);
        localHits.set(0);
        log.info("Blacklist statistics reset");
    }

    /**
     * Blacklist statistics DTO - Enhanced
     */
    public static class BlacklistStats {
        public int redisSize = 0;
        public int localSize = 0;
        public int positiveCacheSize = 0;
        public long cacheHits = 0;
        public long cacheMisses = 0;
        public long redisHits = 0;
        public long localHits = 0;
        public double hitRate = 0;

        public int getTotalSize() {
            return redisSize + localSize;
        }

        @Override
        public String toString() {
            return String.format("BlacklistStats{redis=%d, local=%d, cache=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                    redisSize, localSize, positiveCacheSize, cacheHits, cacheMisses, hitRate);
        }
    }
}