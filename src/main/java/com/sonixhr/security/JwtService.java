package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Service
@SuppressWarnings("null")
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    public Long getExpiration() {
        return expiration;
    }

    public Long getRefreshExpiration() {
        return refreshExpiration;
    }

    @Autowired
    @Lazy
    private PlatformTokenBlacklistService platformBlacklistService;

    @Autowired
    @Lazy
    private TokenBlacklistService tenantBlacklistService;

    // ========================
    // EXTRACT METHODS
    // ========================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public Long extractTenantIdAsLong(String token) {
        String tenantId = extractTenantId(token);
        return tenantId != null ? Long.parseLong(tenantId) : null;
    }

    public String extractUserType(String token) {
        return extractClaim(token, claims -> claims.get("userType", String.class));
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

    public Long extractEmployeeId(String token) {
        return extractClaim(token, claims -> claims.get("employeeId", Long.class));
    }

    public String extractEmployeeCode(String token) {
        return extractClaim(token, claims -> claims.get("employeeCode", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ========================
    // TOKEN GENERATION FOR EMPLOYEE (Tenant users)
    // ========================

    public String generateEmployeeToken(Employee employee) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "EMPLOYEE");
        claims.put("tenantId", employee.getTenantId().toString());
        claims.put("employeeId", employee.getId());
        claims.put("employeeCode", employee.getEmployeeCode());
        claims.put("roles", employee.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());
        claims.put("tokenType", "ACCESS");
        claims.put("fullName", employee.getFullName());
        claims.put("email", employee.getEmail());

        return createToken(claims, employee.getEmail(), expiration);
    }

    public String generateEmployeeRefreshToken(Employee employee) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "EMPLOYEE");
        claims.put("tenantId", employee.getTenantId().toString());
        claims.put("employeeId", employee.getId());
        claims.put("employeeCode", employee.getEmployeeCode());
        claims.put("roles", employee.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());
        claims.put("tokenType", "REFRESH");

        return createToken(claims, employee.getEmail(), refreshExpiration);
    }

    public TokenPair generateEmployeeTokenPair(Employee employee) {
        return TokenPair.builder()
                .accessToken(generateEmployeeToken(employee))
                .refreshToken(generateEmployeeRefreshToken(employee))
                .tokenType("Bearer")
                .expiresIn(expiration)
                .refreshExpiresIn(refreshExpiration)
                .build();
    }

    // ========================
    // TOKEN GENERATION FOR PLATFORM USERS
    // ========================

    public String generatePlatformToken(PlatformUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "PLATFORM");
        claims.put("userId", user.getId());
        claims.put("roles", user.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());
        claims.put("tokenType", "ACCESS");
        claims.put("fullName", user.getFullName());
        claims.put("email", user.getEmail());

        return createToken(claims, user.getEmail(), expiration);
    }

    public String generatePlatformRefreshToken(PlatformUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "PLATFORM");
        claims.put("userId", user.getId());
        claims.put("roles", user.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());
        claims.put("tokenType", "REFRESH");

        return createToken(claims, user.getEmail(), refreshExpiration);
    }

    public TokenPair generatePlatformTokenPair(PlatformUser user) {
        return TokenPair.builder()
                .accessToken(generatePlatformToken(user))
                .refreshToken(generatePlatformRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(expiration)
                .refreshExpiresIn(refreshExpiration)
                .build();
    }

    // ========================
    // COMMON TOKEN CREATION
    // ========================

    private String createToken(Map<String, Object> claims, String subject, long expiryDuration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setId(UUID.randomUUID().toString())
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
            if (token == null || token.isBlank()) {
                return false;
            }
            final String username = extractUsername(token);
            final boolean isExpired = isTokenExpired(token);
            final boolean isBlacklisted = isTokenBlacklisted(token);
            final boolean isAccess = isAccessToken(token);
            return username.equals(userDetails.getUsername()) && !isExpired && !isBlacklisted && isAccess;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token, UserDetails userDetails) {
        try {
            if (token == null || token.isBlank()) {
                return false;
            }
            final String username = extractUsername(token);
            final boolean isExpired = isTokenExpired(token);
            final boolean isBlacklisted = isTokenBlacklisted(token);
            final boolean isRefresh = isRefreshToken(token);
            return username.equals(userDetails.getUsername()) && !isExpired && !isBlacklisted && isRefresh;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return false;
            }
            return !isTokenExpired(token) && !isTokenBlacklisted(token) && isAccessToken(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return false;
            }
            return !isTokenExpired(token) && !isTokenBlacklisted(token) && isRefreshToken(token);
        } catch (Exception e) {
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
            String userType = extractUserType(token);
            if ("PLATFORM".equals(userType)) {
                platformBlacklistService.blacklistToken(token);
            } else if ("EMPLOYEE".equals(userType)) {
                tenantBlacklistService.blacklistToken(token);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate token: {}", e.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        try {
            String userType = extractUserType(token);
            if ("PLATFORM".equals(userType)) {
                return platformBlacklistService.isBlacklisted(token);
            } else if ("EMPLOYEE".equals(userType)) {
                return tenantBlacklistService.isBlacklisted(token);
            }
        } catch (Exception e) {
            log.warn("Failed to check if token is blacklisted: {}", e.getMessage());
        }
        return false;
    }

    // ========================
    // TOKEN REFRESH
    // ========================

    public Optional<String> refreshAccessToken(String refreshToken, UserDetails userDetails) {
        try {
            if (validateRefreshToken(refreshToken, userDetails)) {
                String userType = extractUserType(refreshToken);
                if ("EMPLOYEE".equals(userType) && userDetails instanceof Employee) {
                    return Optional.of(generateEmployeeToken((Employee) userDetails));
                } else if ("PLATFORM".equals(userType) && userDetails instanceof PlatformUser) {
                    return Optional.of(generatePlatformToken((PlatformUser) userDetails));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to refresh access token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ========================
    // HELPER METHODS FOR EMPLOYEE CONTEXT
    // ========================

    public Long getTenantIdFromToken(String token) {
        try {
            return extractTenantIdAsLong(token);
        } catch (Exception e) {
            return null;
        }
    }

    public Long getEmployeeIdFromToken(String token) {
        try {
            return extractEmployeeId(token);
        } catch (Exception e) {
            return null;
        }
    }

    public String getEmployeeCodeFromToken(String token) {
        try {
            return extractEmployeeCode(token);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isEmployeeToken(String token) {
        try {
            return "EMPLOYEE".equals(extractUserType(token));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPlatformToken(String token) {
        try {
            return "PLATFORM".equals(extractUserType(token));
        } catch (Exception e) {
            return false;
        }
    }

    // ========================
    // KEY MANAGEMENT
    // ========================

    private Key getSignKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    public Integer extractRolesVersion(String token) {
        return extractClaim(token, claims -> claims.get("rolesVersion", Integer.class));
    }
}