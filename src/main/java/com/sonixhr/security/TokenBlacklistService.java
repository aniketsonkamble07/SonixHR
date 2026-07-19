package com.sonixhr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.exceptions.TokenValidationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${app.token.blacklist.enabled:true}")
    private boolean blacklistEnabled;

    private static final String REDIS_KEY_BLACKLIST = "token:blacklist:";
    private static final String BLACKLIST_VALUE = "true";

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class ActiveSession {
        private String jti;
        private long expiresAt;
        private Long userId;
        private String clientType;
    }

    // Local in-memory fallback (used when Redis is unavailable)
    private final java.util.Map<String, Long> localBlacklist = new java.util.concurrent.ConcurrentHashMap<>();

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
                boolean redisSuccess = false;
                try {
                    String key = REDIS_KEY_BLACKLIST + jti;
                    redisTemplate.opsForValue().set(key, BLACKLIST_VALUE, ttl, TimeUnit.MILLISECONDS);
                    log.debug("Token blacklisted in Redis: {}, TTL: {}ms", jti, ttl);
                    redisSuccess = true;
                } catch (Exception e) {
                    log.warn("Failed to blacklist token in Redis, falling back to local memory: {}", e.getMessage());
                }
                if (!redisSuccess) {
                    localBlacklist.put(jti, System.currentTimeMillis() + ttl);
                }
            } else {
                log.debug("Token already expired, no need to blacklist: {}", jti);
            }
        } catch (ExpiredJwtException e) {
            log.debug("Token already expired, no need to blacklist: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
            throw new TokenValidationException("Token validation failed for blacklisting: " + e.getMessage(), e);
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

            boolean isBlacklisted = false;
            try {
                String key = REDIS_KEY_BLACKLIST + jti;
                isBlacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            } catch (Exception e) {
                log.warn("Redis blacklist check failed, checking local memory: {}", e.getMessage());
            }

            if (!isBlacklisted) {
                Long expiry = localBlacklist.get(jti);
                if (expiry != null) {
                    if (expiry > System.currentTimeMillis()) {
                        isBlacklisted = true;
                    } else {
                        localBlacklist.remove(jti);
                    }
                }
            }

            return isBlacklisted;
        } catch (Exception e) {
            log.debug("Error checking blacklist: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTenantBlacklisted(Long tenantId) {
        if (!blacklistEnabled || tenantId == null) {
            return false;
        }
        try {
            String key = "tenant:blacklist:" + tenantId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Failed to check tenant blacklist in Redis: {}", e.getMessage());
            return false;
        }
    }

    public void blacklistTenant(Long tenantId) {
        if (!blacklistEnabled || tenantId == null) {
            return;
        }
        try {
            String key = "tenant:blacklist:" + tenantId;
            // 365-day TTL prevents permanent Redis lock if removeFromTenantBlacklist()
            // is never called (e.g. crash after suspension before re-activation).
            redisTemplate.opsForValue().set(key, BLACKLIST_VALUE, 365, TimeUnit.DAYS);
            log.info("Tenant suspended and blacklisted: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to blacklist tenant in Redis: {}", e.getMessage());
        }
    }

    public void removeFromTenantBlacklist(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        try {
            String key = "tenant:blacklist:" + tenantId;
            redisTemplate.delete(key);
            log.info("Tenant removed from blacklist: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to remove tenant from blacklist in Redis: {}", e.getMessage());
        }
    }

    /**
     * Remove a token from blacklist (for testing/admin)
     */
    public void removeFromBlacklist(String token) {
        try {
            String jti = jwtService.extractJti(token);
            if (jti != null) {
                try {
                    String key = REDIS_KEY_BLACKLIST + jti;
                    redisTemplate.delete(key);
                } catch (Exception e) {
                    log.warn("Failed to remove token from Redis blacklist: {}", e.getMessage());
                }
                localBlacklist.remove(jti);
                log.info("Token removed from blacklist: {}", jti);
            }
        } catch (Exception e) {
            log.warn("Failed to remove token from blacklist: {}", e.getMessage());
        }
    }

    /** Purge expired entries from the local in-memory fallback every hour. */
    @Scheduled(fixedDelayString = "${app.token.blacklist.cleanup-interval:3600000}")
    public void cleanupLocalBlacklist() {
        int before = localBlacklist.size();
        localBlacklist.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
        int removed = before - localBlacklist.size();
        if (removed > 0) {
            log.debug("Local blacklist cleaned: removed {} expired entries", removed);
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
            String previousSessionJson = redisTemplate.opsForValue().get(sessionKey);
            if (previousSessionJson != null) {
                String oldJti = null;
                long expiryTime = 0;

                // Backward-compatible format check
                if (previousSessionJson.trim().startsWith("{")) {
                    try {
                        ActiveSession prevSession = objectMapper.readValue(previousSessionJson, ActiveSession.class);
                        oldJti = prevSession.getJti();
                        expiryTime = prevSession.getExpiresAt();
                    } catch (Exception e) {
                        log.warn("Failed to parse JSON active session: {}", e.getMessage());
                    }
                } else {
                    // Fallback to legacy colon-separated string: "JTI:expiryTimestamp"
                    String[] parts = previousSessionJson.split(":");
                    if (parts.length == 2) {
                        oldJti = parts[0];
                        try {
                            expiryTime = Long.parseLong(parts[1]);
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse legacy expiry timestamp: {}", parts[1]);
                        }
                    }
                }

                if (oldJti != null && expiryTime > 0) {
                    // Invalidate the previous token
                    long ttl = expiryTime - System.currentTimeMillis();
                    if (ttl > 0) {
                        String blacklistKey = REDIS_KEY_BLACKLIST + oldJti;
                        redisTemplate.opsForValue().set(blacklistKey, BLACKLIST_VALUE, ttl, TimeUnit.MILLISECONDS);
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
                // Store active session DTO as JSON in Redis
                ActiveSession activeSession = ActiveSession.builder()
                        .jti(newJti)
                        .expiresAt(expiration.getTime())
                        .userId(userId)
                        .clientType(type)
                        .build();
                String value = objectMapper.writeValueAsString(activeSession);
                if (value != null) {
                    redisTemplate.opsForValue().set(sessionKey, value, newTtl, TimeUnit.MILLISECONDS);
                }
                log.debug("Registered active session for user {} on client type {}: JTI {}", userId, type, newJti);
            }
        } catch (Exception e) {
            log.warn("Failed to register active session for user: {}, error: {}", userId, e.getMessage());
        }
    }
}