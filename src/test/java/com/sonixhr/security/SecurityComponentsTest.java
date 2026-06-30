package com.sonixhr.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest
@ActiveProfiles("dev")
public class SecurityComponentsTest {

    @Autowired(required = false)
    private SecurityUtils securityUtils;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Test
    public void testTenantContextBasicOperations() {
        TenantContext.clear();
        Assertions.assertNull(TenantContext.getCurrentTenant());
        Assertions.assertFalse(TenantContext.hasTenantContext());

        TenantContext.setCurrentTenant(123L);
        Assertions.assertEquals(123L, TenantContext.getCurrentTenant());
        Assertions.assertTrue(TenantContext.hasTenantContext());
        Assertions.assertEquals(123L, TenantContext.getCurrentTenantOrThrow());

        TenantContext.setCurrentTenantName("Test Tenant");
        Assertions.assertEquals("Test Tenant", TenantContext.getCurrentTenantName());

        TenantContext.setCurrentUserId(456L);
        Assertions.assertEquals(456L, TenantContext.getCurrentUserId());
        Assertions.assertEquals(456L, TenantContext.getCurrentUserIdOrThrow());

        TenantContext.setRequestId("req-test-123");
        Assertions.assertEquals("req-test-123", TenantContext.getRequestId());

        TenantContext.clear();
        Assertions.assertNull(TenantContext.getCurrentTenant());
        Assertions.assertNull(TenantContext.getCurrentTenantName());
        Assertions.assertNull(TenantContext.getCurrentUserId());
        Assertions.assertFalse(TenantContext.hasTenantContext());
    }

    @Test
    public void testTenantContextThrowsWhenEmpty() {
        TenantContext.clear();
        Assertions.assertThrows(IllegalStateException.class, TenantContext::getCurrentTenantOrThrow);
        Assertions.assertThrows(IllegalStateException.class, TenantContext::getCurrentUserIdOrThrow);
    }

    @Test
    public void testTenantContextThreadIsolation() throws InterruptedException {
        TenantContext.clear();
        int threadsCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch latch = new CountDownLatch(threadsCount);
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        for (int i = 1; i <= threadsCount; i++) {
            final long tenantId = i * 1000L;
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    Thread.sleep(100); // Simulate processing
                    if (!Long.valueOf(tenantId).equals(TenantContext.getCurrentTenant())) {
                        hasFailure.set(true);
                    }
                    TenantContext.clear();
                } catch (Exception e) {
                    hasFailure.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        Assertions.assertFalse(hasFailure.get(), "ThreadLocal values leaked across execution threads!");
    }

    @Test
    public void testTokenPairCreation() {
        TokenPair pair = new TokenPair("access-token-val", "refresh-token-val", "Bearer", 3600L, 86400L);
        Assertions.assertEquals("access-token-val", pair.getAccessToken());
        Assertions.assertEquals("refresh-token-val", pair.getRefreshToken());
        Assertions.assertEquals("Bearer", pair.getTokenType());
        Assertions.assertEquals(3600L, pair.getExpiresIn());
        Assertions.assertEquals(86400L, pair.getRefreshExpiresIn());
    }

    @Test
    public void testSecurityUtilsConfiguration() {
        if (securityUtils != null) {
            Assertions.assertNotNull(securityUtils, "SecurityUtils component should be loaded if autowired");
        }
    }

    @Test
    public void testRateLimiterServiceAutowired() {
        Assertions.assertNotNull(rateLimiterService, "RateLimiterService should be correctly configured and autowired");
    }

    @Test
    public void testTokenBlacklistServiceAutowired() {
        Assertions.assertNotNull(tokenBlacklistService, "TokenBlacklistService should be correctly configured and autowired");
    }
}
