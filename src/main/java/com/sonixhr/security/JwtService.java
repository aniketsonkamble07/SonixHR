package com.sonixhr.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    // Blacklist for invalidated tokens
    private final Set<String> tokenBlacklist = ConcurrentHashMap.newKeySet();

    // ========================
    // EXTRACT METHODS
    // ========================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public UUID extractTenantIdAsUUID(String token) {
        String tenantId = extractTenantId(token);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            throw e;
        }
    }

    // ========================
    // TOKEN GENERATION
    // ========================

    public String generateToken(UserDetails userDetails, String tenantId) {
        return generateToken(userDetails, tenantId, false);
    }

    public String generateToken(UserDetails userDetails, String tenantId, boolean isRefreshToken) {
        Map<String, Object> claims = new HashMap<>();

        claims.put("tenantId", tenantId);
        claims.put("roles", userDetails.getAuthorities()
                .stream()
                .map(auth -> auth.getAuthority())
                .toList());
        claims.put("tokenType", isRefreshToken ? "REFRESH" : "ACCESS");
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iat", new Date().getTime());

        long expiryDuration = isRefreshToken ? refreshExpiration : expiration;

        return createToken(claims, userDetails.getUsername(), expiryDuration);
    }

    public String generateRefreshToken(UserDetails userDetails, String tenantId) {
        return generateToken(userDetails, tenantId, true);
    }

    public TokenPair generateTokenPair(UserDetails userDetails, String tenantId) {
        return TokenPair.builder()
                .accessToken(generateToken(userDetails, tenantId, false))
                .refreshToken(generateToken(userDetails, tenantId, true))
                .tokenType("Bearer")
                .expiresIn(expiration)
                .refreshExpiresIn(refreshExpiration)
                .build();
    }

    private String createToken(Map<String, Object> claims, String subject, long expiryDuration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setId((String) claims.get("jti"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiryDuration))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ========================
    // TOKEN VALIDATION
    // ========================

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            final boolean isExpired = isTokenExpired(token);
            final boolean isBlacklisted = isTokenBlacklisted(token);

            return username.equals(userDetails.getUsername()) && !isExpired && !isBlacklisted;
        } catch (JwtException e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token) && !isTokenBlacklisted(token);
        } catch (JwtException e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isRefreshToken(String token) {
        String tokenType = extractTokenType(token);
        return "REFRESH".equals(tokenType);
    }

    public boolean isAccessToken(String token) {
        String tokenType = extractTokenType(token);
        return "ACCESS".equals(tokenType);
    }

    // ========================
    // TOKEN BLACKLIST
    // ========================

    public void invalidateToken(String token) {
        try {
            String jti = extractJti(token);
            Date expiration = extractExpiration(token);
            long ttl = expiration.getTime() - System.currentTimeMillis();

            if (ttl > 0) {
                tokenBlacklist.add(jti);
                log.info("Token invalidated: {}", jti);

                // Schedule removal after token expires
                scheduleBlacklistRemoval(jti, ttl);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate token: {}", e.getMessage());
        }
    }

    public void invalidateAllUserTokens(String username) {
        log.info("Invalidating all tokens for user: {}", username);
        // In production, implement with Redis
    }

    public boolean isTokenBlacklisted(String token) {
        try {
            String jti = extractJti(token);
            return tokenBlacklist.contains(jti);
        } catch (Exception e) {
            return false;
        }
    }

    private void scheduleBlacklistRemoval(String jti, long ttl) {
        new Thread(() -> {
            try {
                Thread.sleep(ttl);
                tokenBlacklist.remove(jti);
                log.debug("Token removed from blacklist: {}", jti);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // ========================
    // TOKEN REFRESH
    // ========================

    public Optional<String> refreshAccessToken(String refreshToken, UserDetails userDetails) {
        try {
            if (validateToken(refreshToken, userDetails) && isRefreshToken(refreshToken)) {
                String tenantId = extractTenantId(refreshToken);
                String newAccessToken = generateToken(userDetails, tenantId, false);
                return Optional.of(newAccessToken);
            }
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ========================
    // KEY MANAGEMENT
    // ========================

    private Key getSignKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ========================
    // INNER CLASSES
    // ========================

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private Long refreshExpiresIn;
    }
}