package com.sonixhr.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Token-bucket rate limiter backed by Redis.
 *
 * Each unique key gets a counter in Redis that is incremented on every request.
 * On the first request the key is created with a TTL so it automatically resets
 * after the window expires. If the counter exceeds maxRequests a 429 is thrown.
 *
 * Usage:
 *   rateLimiterService.checkOrThrow("login:ip:" + ip, 10, 60);
 *
 * Why Redis?
 *   - Works correctly across multiple app instances (unlike an in-memory map).
 *   - TTL-based expiry means no background cleanup thread needed.
 *   - Atomic increment (INCR) avoids race conditions.
 *
 * Limitations:
 *   - Fixed window, not sliding window — up to 2x the limit is possible at window
 *     boundaries. Acceptable for most auth rate-limiting use cases.
 *   - If Redis is unavailable the limiter fails OPEN (requests pass through) to
 *     avoid a Redis outage taking down authentication entirely. Adjust failOpen
 *     below if you want stricter behaviour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    // Fail open by default — a Redis outage should not lock everyone out.
    // Set to false if you prefer to reject requests when Redis is unavailable.
    private static final boolean FAIL_OPEN = true;

    /**
     * Increments the request counter for the given key.
     * Throws 429 if maxRequests is exceeded within windowSeconds.
     *
     * @param key            unique bucket identifier (e.g. "login:ip:1.2.3.4")
     * @param maxRequests    maximum allowed requests in the window
     * @param windowSeconds  window length in seconds
     */
    public void checkOrThrow(String key, int maxRequests, long windowSeconds) {
        try {
            String redisKey = "ratelimit:" + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);

            if (count == null) {
                log.warn("Rate limiter got null from Redis for key: {}", redisKey);
                if (!FAIL_OPEN) throw rateLimitException(key);
                return;
            }

            if (count == 1) {
                // First request in this window — set the TTL.
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }

            if (count > maxRequests) {
                log.warn("Rate limit exceeded for key: {} (count={}, max={})", redisKey, count, maxRequests);
                throw rateLimitException(key);
            }

        } catch (ResponseStatusException e) {
            throw e; // re-throw 429 as-is
        } catch (Exception e) {
            log.error("Rate limiter error for key: {}. Failing {}.", key, FAIL_OPEN ? "open" : "closed", e);
            if (!FAIL_OPEN) throw rateLimitException(key);
        }
    }

    /**
     * Returns the remaining request count for a key without incrementing.
     * Returns -1 if the key doesn't exist or Redis is unavailable.
     */
    public long remaining(String key, int maxRequests) {
        try {
            String value = redisTemplate.opsForValue().get("ratelimit:" + key);
            if (value == null) return maxRequests;
            long used = Long.parseLong(value);
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            log.warn("Could not read remaining rate limit for: {}", key, e);
            return -1;
        }
    }

    /**
     * Resets the bucket for a key (e.g. after a successful login to clear the
     * failed-attempt counter).
     */
    public void reset(String key) {
        try {
            redisTemplate.delete("ratelimit:" + key);
        } catch (Exception e) {
            log.warn("Could not reset rate limit for: {}", key, e);
        }
    }

    private ResponseStatusException rateLimitException(String key) {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later."
        );
    }
}