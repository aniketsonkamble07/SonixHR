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
}