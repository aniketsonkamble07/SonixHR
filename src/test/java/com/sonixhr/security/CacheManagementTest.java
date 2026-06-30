package com.sonixhr.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.sonixhr.entity.employee.Employee;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("dev")
@SuppressWarnings("unchecked")
public class CacheManagementTest {

    @Autowired
    private PlatformTokenBlacklistService platformTokenBlacklistService;

    @Autowired
    private JwtService jwtService;

    private Employee mockEmployee;
    private Cache<String, Object> positiveCache;
    private Map<String, Object> localBlacklist;

    @BeforeEach
    public void setUp() {
        positiveCache = (Cache<String, Object>) ReflectionTestUtils.getField(platformTokenBlacklistService, "positiveCache");
        localBlacklist = (Map<String, Object>) ReflectionTestUtils.getField(platformTokenBlacklistService, "localBlacklist");

        positiveCache.invalidateAll();
        localBlacklist.clear();

        mockEmployee = Mockito.mock(Employee.class);
        Mockito.when(mockEmployee.getId()).thenReturn(100L);
        Mockito.when(mockEmployee.getTenantId()).thenReturn(2L);
        Mockito.when(mockEmployee.getEmployeeCode()).thenReturn("EMP-CACHE-MGMT");
        Mockito.when(mockEmployee.getEmail()).thenReturn("cachemgmt@sonixhr.com");
        Mockito.when(mockEmployee.getUsername()).thenReturn("cachemgmt@sonixhr.com");
        Mockito.when(mockEmployee.getAuthorities()).thenAnswer(inv -> Collections.emptyList());
    }

    @Test
    public void testPositiveCacheAndStatsScenarios() {
        // CM-01: Positive Cache -> check cache insertion when token is blacklisted
        String token = jwtService.generateEmployeeToken(mockEmployee);
        String jti = jwtService.extractJti(token);

        Assertions.assertNull(positiveCache.getIfPresent(token));

        platformTokenBlacklistService.blacklistToken(token);

        // Assert token is added to positive cache
        Assertions.assertNotNull(positiveCache.getIfPresent(token));
        Assertions.assertNotNull(positiveCache.getIfPresent("jti:" + jti));

        // CM-05: Cache Statistics
        PlatformTokenBlacklistService.BlacklistStats stats = platformTokenBlacklistService.getStats();
        Assertions.assertNotNull(stats);
        Assertions.assertTrue(stats.positiveCacheSize >= 2); // token + jti entries
        Assertions.assertNotNull(stats.hitRate);
    }

    @Test
    public void testCacheTtlAndSizePolicies() {
        // CM-02: Cache TTL -> check that positiveCache has a write TTL of 300 seconds (5 mins)
        Optional<Long> expiresAfterWrite = positiveCache.policy().expireAfterWrite().map(policy -> policy.getExpiresAfter(TimeUnit.SECONDS));
        Assertions.assertTrue(expiresAfterWrite.isPresent());
        Assertions.assertEquals(300L, expiresAfterWrite.get());

        // CM-03: Cache Size Limit -> check that positiveCache has a maximum size configured
        Optional<Long> maximumSize = positiveCache.policy().eviction().map(policy -> policy.getMaximum());
        Assertions.assertTrue(maximumSize.isPresent());
        int localCacheSize = (int) ReflectionTestUtils.getField(platformTokenBlacklistService, "localCacheSize");
        Assertions.assertEquals((long) localCacheSize, maximumSize.get());
    }

    @Test
    public void testCacheCleanupScheduler() throws Exception {
        // CM-04: Cache Cleanup -> verify scheduled cleanup removes expired local entries
        String expiredJti = "expired-jti-12345";
        
        // Instantiate BlacklistEntry using reflection since it is a private static class
        Class<?> entryClass = Class.forName("com.sonixhr.security.PlatformTokenBlacklistService$BlacklistEntry");
        java.lang.reflect.Constructor<?> constructor = entryClass.getDeclaredConstructor(
                String.class, String.class, String.class, long.class);
        constructor.setAccessible(true);
        
        // Create an entry that expired 5 seconds ago
        long expiredTime = System.currentTimeMillis() - 5000;
        Object expiredEntry = constructor.newInstance(expiredJti, "cachemgmt@sonixhr.com", "EMPLOYEE", expiredTime);

        // Put expired entry directly into localBlacklist map
        localBlacklist.put(expiredJti, expiredEntry);
        Assertions.assertEquals(1, localBlacklist.size());

        // Trigger manual cleanup call
        platformTokenBlacklistService.cleanupLocalBlacklist();

        // Assert local blacklist was cleaned up
        Assertions.assertEquals(0, localBlacklist.size());
    }
}
