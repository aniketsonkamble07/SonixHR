package com.sonixhr.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtService jwtService;

    @Value("${app.token.blacklist.enabled:true}")
    private boolean blacklistEnabled;

    private static final String REDIS_KEY_BLACKLIST = "token:blacklist:";

    /**
     * Blacklist a token
     */
    public void blacklistToken(String token) {
        if (!blacklistEnabled) {
            log.debug("Token blacklist is disabled");
            return;
        }

        try {
            Claims claims = jwtService.extractAllClaims(token);
            String jti = claims.getId();
            Date expiration = claims.getExpiration();

            long ttl = expiration.getTime() - System.currentTimeMillis();

            if (ttl > 0) {
                String key = REDIS_KEY_BLACKLIST + jti;
                redisTemplate.opsForValue().set(key, "true", ttl, TimeUnit.MILLISECONDS);
                log.debug("Token blacklisted: {}, TTL: {}ms", jti, ttl);
            } else {
                log.debug("Token already expired, no need to blacklist: {}", jti);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    /**
     * Check if a token is blacklisted
     */
    public boolean isBlacklisted(String token) {
        if (!blacklistEnabled) {
            return false;
        }

        try {
            String jti = jwtService.extractJti(token);
            if (jti == null) {
                return false;
            }

            String key = REDIS_KEY_BLACKLIST + jti;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.debug("Error checking blacklist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove a token from blacklist (for testing/admin)
     */
    public void removeFromBlacklist(String token) {
        try {
            String jti = jwtService.extractJti(token);
            if (jti != null) {
                String key = REDIS_KEY_BLACKLIST + jti;
                redisTemplate.delete(key);
                log.info("Token removed from blacklist: {}", jti);
            }
        } catch (Exception e) {
            log.warn("Failed to remove token from blacklist: {}", e.getMessage());
        }
    }

    private static final String REDIS_KEY_ACTIVE_SESSION = "user:session:";

    /**
     * Registers a new active session for the employee on the specified client type.
     * If a session already exists for this client type, the old token is blacklisted.
     */
    public void registerActiveSession(Long userId, String clientType, String token) {
        if (!blacklistEnabled || userId == null || token == null) {
            return;
        }

        String type = "MOBILE".equalsIgnoreCase(clientType) ? "MOBILE" : "WEB";
        String sessionKey = REDIS_KEY_ACTIVE_SESSION + userId + ":" + type;

        try {
            // Get previous active session details from Redis
            String previousJtiAndExpiry = redisTemplate.opsForValue().get(sessionKey);
            if (previousJtiAndExpiry != null) {
                // Invalidate the previous token
                String[] parts = previousJtiAndExpiry.split(":");
                if (parts.length == 2) {
                    String oldJti = parts[0];
                    long expiryTime = Long.parseLong(parts[1]);
                    long ttl = expiryTime - System.currentTimeMillis();
                    if (ttl > 0) {
                        String blacklistKey = REDIS_KEY_BLACKLIST + oldJti;
                        redisTemplate.opsForValue().set(blacklistKey, "true", ttl, TimeUnit.MILLISECONDS);
                        log.info("Kicked out previous session for user {} on client type {}: JTI {}", userId, type, oldJti);
                    }
                }
            }

            // Extract claims of the new token to register it
            Claims claims = jwtService.extractAllClaims(token);
            String newJti = claims.getId();
            Date expiration = claims.getExpiration();
            long newTtl = expiration.getTime() - System.currentTimeMillis();

            if (newTtl > 0) {
                // Store "newJti:expiryTimestamp" in Redis
                String value = newJti + ":" + expiration.getTime();
                redisTemplate.opsForValue().set(sessionKey, value, newTtl, TimeUnit.MILLISECONDS);
                log.debug("Registered active session for user {} on client type {}: JTI {}", userId, type, newJti);
            }
        } catch (Exception e) {
            log.warn("Failed to register active session for user: {}, error: {}", userId, e.getMessage());
        }
    }
}