package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("dev")
@SuppressWarnings("unchecked")
public class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenBlacklistService tenantBlacklistService;

    @Autowired
    private PlatformTokenBlacklistService platformBlacklistService;

    @Value("${app.jwt.secret}")
    private String secret;

    @Test
    public void testGenerateEmployeeAccessToken() { // TG-01
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(123L);
        Mockito.when(employee.getTenantId()).thenReturn(456L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-999");
        Mockito.when(employee.getEmail()).thenReturn("employee@sonixhr.com");
        Mockito.when(employee.getFullName()).thenReturn("John Doe");

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("LEAVE_REQUEST"),
                new SimpleGrantedAuthority("LEAVE_VIEW_OWN")
        );
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertNotNull(token);

        // Assert claims
        Assertions.assertEquals("employee@sonixhr.com", jwtService.extractUsername(token));
        Assertions.assertEquals("EMPLOYEE", jwtService.extractUserType(token));
        Assertions.assertEquals(456L, jwtService.extractTenantIdAsLong(token));
        Assertions.assertEquals(123L, jwtService.extractEmployeeId(token));
        Assertions.assertEquals("EMP-999", jwtService.extractEmployeeCode(token));
        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(token));

        List<String> roles = jwtService.extractRoles(token);
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.contains("LEAVE_REQUEST"));
        Assertions.assertTrue(roles.contains("LEAVE_VIEW_OWN"));
    }

    @Test
    public void testGenerateEmployeeRefreshToken() { // TG-02
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(123L);
        Mockito.when(employee.getTenantId()).thenReturn(456L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-999");
        Mockito.when(employee.getEmail()).thenReturn("employee@sonixhr.com");

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generateEmployeeRefreshToken(employee);
        Assertions.assertNotNull(token);
        Assertions.assertEquals("employee@sonixhr.com", jwtService.extractUsername(token));
        Assertions.assertEquals("REFRESH", jwtService.extractTokenType(token));
        Assertions.assertTrue(jwtService.isRefreshToken(token));
    }

    @Test
    public void testGenerateEmployeeTokenPair() { // TG-03
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(123L);
        Mockito.when(employee.getTenantId()).thenReturn(456L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-999");
        Mockito.when(employee.getEmail()).thenReturn("employee@sonixhr.com");

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> authorities);

        TokenPair pair = jwtService.generateEmployeeTokenPair(employee);
        Assertions.assertNotNull(pair);
        Assertions.assertEquals("Bearer", pair.getTokenType());
        Assertions.assertNotNull(pair.getAccessToken());
        Assertions.assertNotNull(pair.getRefreshToken());

        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(pair.getAccessToken()));
        Assertions.assertEquals("REFRESH", jwtService.extractTokenType(pair.getRefreshToken()));
    }

    @Test
    public void testGeneratePlatformAccessToken() { // TG-04
        PlatformUser user = Mockito.mock(PlatformUser.class);
        Mockito.when(user.getId()).thenReturn(789L);
        Mockito.when(user.getEmail()).thenReturn("admin@sonixhr.com");
        Mockito.when(user.getFullName()).thenReturn("Super Admin");

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("TENANT_CREATE"),
                new SimpleGrantedAuthority("TENANT_SUSPEND")
        );
        Mockito.when(user.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generatePlatformToken(user);
        Assertions.assertNotNull(token);

        Assertions.assertEquals("admin@sonixhr.com", jwtService.extractUsername(token));
        Assertions.assertEquals("PLATFORM", jwtService.extractUserType(token));
        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(token));

        List<String> roles = jwtService.extractRoles(token);
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.contains("TENANT_CREATE"));
        Assertions.assertTrue(roles.contains("TENANT_SUSPEND"));
    }

    @Test
    public void testGeneratePlatformRefreshToken() { // TG-05
        PlatformUser user = Mockito.mock(PlatformUser.class);
        Mockito.when(user.getId()).thenReturn(789L);
        Mockito.when(user.getEmail()).thenReturn("admin@sonixhr.com");

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Mockito.when(user.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generatePlatformRefreshToken(user);
        Assertions.assertNotNull(token);
        Assertions.assertEquals("admin@sonixhr.com", jwtService.extractUsername(token));
        Assertions.assertEquals("REFRESH", jwtService.extractTokenType(token));
    }

    @Test
    public void testGeneratePlatformTokenPair() { // TG-06
        PlatformUser user = Mockito.mock(PlatformUser.class);
        Mockito.when(user.getId()).thenReturn(789L);
        Mockito.when(user.getEmail()).thenReturn("admin@sonixhr.com");

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Mockito.when(user.getAuthorities()).thenAnswer(inv -> authorities);

        TokenPair pair = jwtService.generatePlatformTokenPair(user);
        Assertions.assertNotNull(pair);
        Assertions.assertEquals("Bearer", pair.getTokenType());
        Assertions.assertNotNull(pair.getAccessToken());
        Assertions.assertNotNull(pair.getRefreshToken());

        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(pair.getAccessToken()));
        Assertions.assertEquals("REFRESH", jwtService.extractTokenType(pair.getRefreshToken()));
    }

    @Test
    public void testTokenWithEmptyClaims() { // TG-07
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("emptyclaims@sonixhr.com");

        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertNotNull(token);
        Assertions.assertEquals("emptyclaims@sonixhr.com", jwtService.extractUsername(token));

        List<String> roles = jwtService.extractRoles(token);
        Assertions.assertTrue(roles == null || roles.isEmpty());
    }

    @Test
    public void testTokenWithSpecialCharacters() { // TG-08
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("special!#$&'*+-/=?^_`{|}~@special-domain.com");

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertNotNull(token);
        Assertions.assertEquals("special!#$&'*+-/=?^_`{|}~@special-domain.com", jwtService.extractUsername(token));
    }

    @Test
    public void testValidateValidToken() { // TV-01
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("validuser@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertNotNull(token);

        UserDetails userDetails = Mockito.mock(UserDetails.class);
        Mockito.when(userDetails.getUsername()).thenReturn("validuser@sonixhr.com");

        Assertions.assertTrue(jwtService.validateToken(token, userDetails));
        Assertions.assertTrue(jwtService.validateToken(token));
    }

    @Test
    public void testValidateExpiredToken() { // TV-02
        java.security.Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        String expiredToken = Jwts.builder()
                .setSubject("expireduser@sonixhr.com")
                .setExpiration(new Date(System.currentTimeMillis() - 10000)) // expired 10s ago
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Assertions.assertFalse(jwtService.validateToken(expiredToken));
    }

    @Test
    public void testValidateTamperedToken() { // TV-03
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("tamper@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        Assertions.assertFalse(jwtService.validateToken(tamperedToken));
    }

    @Test
    public void testValidateBlacklistedToken() { // TV-04
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("blacklist@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertTrue(jwtService.validateToken(token));

        jwtService.invalidateToken(token);
        Assertions.assertFalse(jwtService.validateToken(token));
    }

    @Test
    public void testValidateWithWrongUser() { // TV-05
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("user1@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);

        UserDetails wrongUser = Mockito.mock(UserDetails.class);
        Mockito.when(wrongUser.getUsername()).thenReturn("user2@sonixhr.com");

        Assertions.assertFalse(jwtService.validateToken(token, wrongUser));
    }

    @Test
    public void testValidateMalformedToken() { // TV-06
        Assertions.assertFalse(jwtService.validateToken("malformed.jwt.token"));
    }

    @Test
    public void testValidateEmptyToken() { // TV-07
        Assertions.assertFalse(jwtService.validateToken(null));
        Assertions.assertFalse(jwtService.validateToken(""));
    }

    @Test
    public void testValidateTokenWithInvalidIssuer() { // TV-08
        // Generate with different key
        java.security.Key diffKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String tokenWithDiffKey = Jwts.builder()
                .setSubject("diffkey@sonixhr.com")
                .setExpiration(new Date(System.currentTimeMillis() + 600000))
                .signWith(diffKey, SignatureAlgorithm.HS256)
                .compact();

        Assertions.assertFalse(jwtService.validateToken(tokenWithDiffKey));
    }

    @Test
    public void testTokenExtractions() { // TE-01 to TE-10
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(555L);
        Mockito.when(employee.getTenantId()).thenReturn(777L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-EXTRACTION");
        Mockito.when(employee.getEmail()).thenReturn("extract@sonixhr.com");
        Mockito.when(employee.getFullName()).thenReturn("Extraction Test");

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_MANAGER")
        );
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> authorities);

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertNotNull(token);

        // TE-01
        Assertions.assertEquals("extract@sonixhr.com", jwtService.extractUsername(token));
        // TE-02
        Assertions.assertEquals("777", jwtService.extractTenantId(token));
        // TE-03
        Assertions.assertEquals(Long.valueOf(777L), jwtService.extractTenantIdAsLong(token));
        // TE-04
        Assertions.assertEquals("EMPLOYEE", jwtService.extractUserType(token));
        // TE-05
        List<String> roles = jwtService.extractRoles(token);
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.contains("ROLE_USER"));
        Assertions.assertTrue(roles.contains("ROLE_MANAGER"));
        // TE-06
        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(token));
        // TE-07
        Assertions.assertNotNull(jwtService.extractJti(token));
        // TE-08
        Assertions.assertEquals(Long.valueOf(555L), jwtService.extractEmployeeId(token));
        // TE-09
        Assertions.assertNotNull(jwtService.extractExpiration(token));
        Assertions.assertTrue(jwtService.extractExpiration(token).after(new Date()));

        // TE-10
        java.security.Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        String manualToken = Jwts.builder()
                .claim("rolesVersion", 12)
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        Assertions.assertEquals(Integer.valueOf(12), jwtService.extractRolesVersion(manualToken));
    }

    @Test
    public void testRefreshEmployeeToken() { // TR-01
        Employee employeeDetails = Mockito.mock(Employee.class);
        Mockito.when(employeeDetails.getId()).thenReturn(1L);
        Mockito.when(employeeDetails.getTenantId()).thenReturn(2L);
        Mockito.when(employeeDetails.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employeeDetails.getEmail()).thenReturn("refreshemp@sonixhr.com");
        Mockito.when(employeeDetails.getUsername()).thenReturn("refreshemp@sonixhr.com");
        Mockito.when(employeeDetails.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String refreshToken = jwtService.generateEmployeeRefreshToken(employeeDetails);
        Assertions.assertNotNull(refreshToken);

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken(refreshToken, employeeDetails);
        Assertions.assertTrue(refreshed.isPresent());
        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(refreshed.get()));
        Assertions.assertEquals("refreshemp@sonixhr.com", jwtService.extractUsername(refreshed.get()));
    }

    @Test
    public void testRefreshPlatformToken() { // TR-02
        PlatformUser user = Mockito.mock(PlatformUser.class);
        Mockito.when(user.getId()).thenReturn(100L);
        Mockito.when(user.getEmail()).thenReturn("refreshplat@sonixhr.com");
        Mockito.when(user.getUsername()).thenReturn("refreshplat@sonixhr.com");
        Mockito.when(user.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String refreshToken = jwtService.generatePlatformRefreshToken(user);
        Assertions.assertNotNull(refreshToken);

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken(refreshToken, user);
        Assertions.assertTrue(refreshed.isPresent());
        Assertions.assertEquals("ACCESS", jwtService.extractTokenType(refreshed.get()));
        Assertions.assertEquals("refreshplat@sonixhr.com", jwtService.extractUsername(refreshed.get()));
    }

    @Test
    public void testRefreshWithExpiredRefreshToken() { // TR-03
        java.security.Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        String expiredRefreshToken = Jwts.builder()
                .claim("userType", "EMPLOYEE")
                .claim("tokenType", "REFRESH")
                .setSubject("expiredrefresh@sonixhr.com")
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getUsername()).thenReturn("expiredrefresh@sonixhr.com");

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken(expiredRefreshToken, employee);
        Assertions.assertFalse(refreshed.isPresent());
    }

    @Test
    public void testRefreshWithInvalidToken() { // TR-04
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getUsername()).thenReturn("invalid@sonixhr.com");

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken("invalid-token-string", employee);
        Assertions.assertFalse(refreshed.isPresent());
    }

    @Test
    public void testRefreshWithAccessToken() { // TR-05
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("access@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String accessToken = jwtService.generateEmployeeToken(employee);

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken(accessToken, employee);
        Assertions.assertFalse(refreshed.isPresent());
    }

    @Test
    public void testRefreshWithBlacklistedToken() { // TR-06
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("blacklistrefresh@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String refreshToken = jwtService.generateEmployeeRefreshToken(employee);
        jwtService.invalidateToken(refreshToken);

        java.util.Optional<String> refreshed = jwtService.refreshAccessToken(refreshToken, employee);
        Assertions.assertFalse(refreshed.isPresent());
    }

    @Test
    public void testBlacklistEmployeeToken() { // TB-01
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("blacklistemp@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        Assertions.assertFalse(jwtService.isTokenBlacklisted(token));

        jwtService.invalidateToken(token);
        Assertions.assertTrue(jwtService.isTokenBlacklisted(token));
    }

    @Test
    public void testBlacklistPlatformToken() { // TB-02
        PlatformUser user = Mockito.mock(PlatformUser.class);
        Mockito.when(user.getId()).thenReturn(1L);
        Mockito.when(user.getEmail()).thenReturn("blacklistplat@sonixhr.com");
        Mockito.when(user.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generatePlatformToken(user);
        Assertions.assertFalse(jwtService.isTokenBlacklisted(token));

        jwtService.invalidateToken(token);
        Assertions.assertTrue(jwtService.isTokenBlacklisted(token));
    }

    @Test
    public void testBlacklistAlreadyExpiredToken() { // TB-03
        java.security.Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        String expiredToken = Jwts.builder()
                .claim("userType", "EMPLOYEE")
                .setSubject("alreadyexpired@sonixhr.com")
                .setExpiration(new Date(System.currentTimeMillis() - 600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Calling invalidateToken on already expired token should perform no action/not throw exception
        Assertions.assertDoesNotThrow(() -> jwtService.invalidateToken(expiredToken));
    }

    @Test
    public void testBlacklistWithRedisFailure() throws Exception { // TB-04
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("redisfail@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);

        // Swap the redisTemplate inside tenantBlacklistService using reflection with a mock that throws exception
        org.springframework.data.redis.core.RedisTemplate<String, String> originalRedis = 
                (org.springframework.data.redis.core.RedisTemplate<String, String>) 
                org.springframework.test.util.ReflectionTestUtils.getField(tenantBlacklistService, "redisTemplate");
        try {
            org.springframework.data.redis.core.RedisTemplate<String, String> mockRedis = Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class);
            org.springframework.data.redis.core.ValueOperations<String, String> mockOps = Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
            Mockito.doThrow(new RuntimeException("Redis connection failed")).when(mockOps).set(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.any());
            Mockito.when(mockRedis.opsForValue()).thenReturn(mockOps);
            Mockito.doThrow(new RuntimeException("Redis connection failed")).when(mockRedis).hasKey(Mockito.anyString());

            org.springframework.test.util.ReflectionTestUtils.setField(tenantBlacklistService, "redisTemplate", mockRedis);

            // Invalidate token -> should catch Redis error and fallback to local memory in TokenBlacklistService
            jwtService.invalidateToken(token);

            // Check if blacklisted -> should check local memory since Redis fails
            Assertions.assertTrue(jwtService.isTokenBlacklisted(token));
        } finally {
            // Restore original redisTemplate
            org.springframework.test.util.ReflectionTestUtils.setField(tenantBlacklistService, "redisTemplate", originalRedis);
        }
    }

    @Test
    public void testCheckBlacklistedToken() { // TB-05 & TB-06
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("checkblacklist@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        
        // TB-06: Check non-blacklisted status
        Assertions.assertFalse(jwtService.isTokenBlacklisted(token));

        jwtService.invalidateToken(token);

        // TB-05: Check blacklisted status
        Assertions.assertTrue(jwtService.isTokenBlacklisted(token));
    }

    @Test
    public void testRemoveFromBlacklist() { // TB-07
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("removeblacklist@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String token = jwtService.generateEmployeeToken(employee);
        jwtService.invalidateToken(token);
        Assertions.assertTrue(jwtService.isTokenBlacklisted(token));

        tenantBlacklistService.removeFromBlacklist(token);
        Assertions.assertFalse(jwtService.isTokenBlacklisted(token));
    }

    @Test
    public void testTokenTypeIsolation() {
        Employee employee = Mockito.mock(Employee.class);
        Mockito.when(employee.getId()).thenReturn(1L);
        Mockito.when(employee.getTenantId()).thenReturn(2L);
        Mockito.when(employee.getEmployeeCode()).thenReturn("EMP-001");
        Mockito.when(employee.getEmail()).thenReturn("isolation@sonixhr.com");
        Mockito.when(employee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());

        String accessToken = jwtService.generateEmployeeToken(employee);
        String refreshToken = jwtService.generateEmployeeRefreshToken(employee);

        UserDetails userDetails = Mockito.mock(UserDetails.class);
        Mockito.when(userDetails.getUsername()).thenReturn("isolation@sonixhr.com");

        // validateToken should accept access tokens and reject refresh tokens
        Assertions.assertTrue(jwtService.validateToken(accessToken));
        Assertions.assertFalse(jwtService.validateToken(refreshToken));

        // validateRefreshToken should accept refresh tokens and reject access tokens
        Assertions.assertTrue(jwtService.validateRefreshToken(refreshToken));
        Assertions.assertFalse(jwtService.validateRefreshToken(accessToken));
    }

    @Test
    public void testTenantSuspensionBlacklist() {
        Long tenantId = 9999L;
        Assertions.assertFalse(tenantBlacklistService.isTenantBlacklisted(tenantId));

        tenantBlacklistService.blacklistTenant(tenantId);
        Assertions.assertTrue(tenantBlacklistService.isTenantBlacklisted(tenantId));

        tenantBlacklistService.removeFromTenantBlacklist(tenantId);
        Assertions.assertFalse(tenantBlacklistService.isTenantBlacklisted(tenantId));
    }
}
