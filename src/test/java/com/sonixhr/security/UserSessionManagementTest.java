package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("dev")
public class UserSessionManagementTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private PlatformTokenBlacklistService platformTokenBlacklistService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    private Employee mockEmployee;

    @BeforeEach
    public void setUp() {
        mockEmployee = Mockito.mock(Employee.class);
        Mockito.when(mockEmployee.getId()).thenReturn(9999L);
        Mockito.when(mockEmployee.getTenantId()).thenReturn(2L);
        Mockito.when(mockEmployee.getEmployeeCode()).thenReturn("EMP-SESSION");
        Mockito.when(mockEmployee.getEmail()).thenReturn("sessionuser@sonixhr.com");
        Mockito.when(mockEmployee.getUsername()).thenReturn("sessionuser@sonixhr.com");
        Mockito.when(mockEmployee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());
    }

    @Test
    public void testUserSessionManagementScenarios() { // US-01 to US-05
        String token1 = jwtService.generateEmployeeToken(mockEmployee);
        String jti1 = jwtService.extractJti(token1);

        // US-01 & US-02: Register Active Session with Client Type
        tokenBlacklistService.registerActiveSession(9999L, "WEB", token1);

        String sessionKeyWeb = "user:session:9999:WEB";
        String storedValue = stringRedisTemplate.opsForValue().get(sessionKeyWeb);
        Assertions.assertNotNull(storedValue);
        Assertions.assertTrue(storedValue.startsWith(jti1 + ":"));

        // US-04: Session for Different Device (MOBILE)
        String token2 = jwtService.generateEmployeeToken(mockEmployee);
        String jti2 = jwtService.extractJti(token2);
        tokenBlacklistService.registerActiveSession(9999L, "MOBILE", token2);

        String sessionKeyMobile = "user:session:9999:MOBILE";
        String storedValueMobile = stringRedisTemplate.opsForValue().get(sessionKeyMobile);
        Assertions.assertNotNull(storedValueMobile);
        Assertions.assertTrue(storedValueMobile.startsWith(jti2 + ":"));

        // Assert both WEB and MOBILE sessions are active (neither token is blacklisted yet)
        Assertions.assertFalse(tokenBlacklistService.isBlacklisted(token1));
        Assertions.assertFalse(tokenBlacklistService.isBlacklisted(token2));

        // US-03: Session Replacement (Same user logs in on WEB again)
        String token3 = jwtService.generateEmployeeToken(mockEmployee);
        String jti3 = jwtService.extractJti(token3);
        tokenBlacklistService.registerActiveSession(9999L, "WEB", token3);

        // Assert old WEB session token (token1) is now blacklisted, while MOBILE (token2) and new WEB (token3) remain active
        Assertions.assertTrue(tokenBlacklistService.isBlacklisted(token1));
        Assertions.assertFalse(tokenBlacklistService.isBlacklisted(token2));
        Assertions.assertFalse(tokenBlacklistService.isBlacklisted(token3));

        // US-05: Session Expiry (verify TTL on keys)
        Long ttlWeb = stringRedisTemplate.getExpire(sessionKeyWeb, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(ttlWeb != null && ttlWeb > 0 && ttlWeb <= jwtService.getExpiration());

        // Cleanup
        stringRedisTemplate.delete(sessionKeyWeb);
        stringRedisTemplate.delete(sessionKeyMobile);
    }

    @Test
    public void testBlacklistAllUserTokens() { // US-06
        String username = "bulkuser@sonixhr.com";
        String userType = "EMPLOYEE";
        String userKey = "token:user-blacklist:" + userType + ":" + username;

        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();

        // Register JTIs to user's blacklist set in Redis
        redisTemplate.opsForSet().add(userKey, jti1, jti2);
        redisTemplate.expire(userKey, 1, TimeUnit.HOURS);

        // Call blacklistAllUserTokens
        platformTokenBlacklistService.blacklistAllUserTokens(username, userType);

        // Verify JTIs are blacklisted
        String blacklistKey1 = "token:blacklist:" + jti1;
        String blacklistKey2 = "token:blacklist:" + jti2;

        Map<Object, Object> hash1 = redisTemplate.opsForHash().entries(blacklistKey1);
        Map<Object, Object> hash2 = redisTemplate.opsForHash().entries(blacklistKey2);

        Assertions.assertFalse(hash1.isEmpty());
        Assertions.assertEquals(jti1, hash1.get("jti"));
        Assertions.assertEquals(username, hash1.get("username"));

        Assertions.assertFalse(hash2.isEmpty());
        Assertions.assertEquals(jti2, hash2.get("jti"));
        Assertions.assertEquals(username, hash2.get("username"));

        // Cleanup
        redisTemplate.delete(userKey);
        redisTemplate.delete(blacklistKey1);
        redisTemplate.delete(blacklistKey2);
    }
}
