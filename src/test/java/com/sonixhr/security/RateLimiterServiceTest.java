package com.sonixhr.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("dev")
public class RateLimiterServiceTest {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SpyBean
    private StringRedisTemplate spyRedisTemplate;

    private String testKey;

    @BeforeEach
    public void setUp() {
        testKey = "test_user_key_" + System.currentTimeMillis();
        rateLimiterService.reset(testKey);
    }

    // =====================================================
    // 6.1 RATE LIMITING SCENARIOS
    // =====================================================

    @Test
    public void testRateLimiting() {
        // RL-01: First request sets TTL & increments
        Assertions.assertDoesNotThrow(() -> rateLimiterService.checkOrThrow(testKey, 2, 60));

        // RL-02: Second request allowed
        Assertions.assertDoesNotThrow(() -> rateLimiterService.checkOrThrow(testKey, 2, 60));

        // RL-03: Third request exceeded (limit is 2)
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            rateLimiterService.checkOrThrow(testKey, 2, 60);
        });
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        // RL-06: Redis failure fallback -> Fail Open
        // Replace internal redisTemplate in rateLimiterService with spyRedisTemplate to simulate exception
        StringRedisTemplate realTemplate = (StringRedisTemplate) org.springframework.test.util.ReflectionTestUtils.getField(rateLimiterService, "redisTemplate");
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "redisTemplate", spyRedisTemplate);
            
            // Stub opsForValue to throw connection exception
            ValueOperations<String, String> mockOps = mock(ValueOperations.class);
            when(spyRedisTemplate.opsForValue()).thenReturn(mockOps);
            when(mockOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));

            // Check should not throw because FAIL_OPEN = true
            Assertions.assertDoesNotThrow(() -> rateLimiterService.checkOrThrow("failure_key", 5, 60));
        } finally {
            // Restore real template
            org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "redisTemplate", realTemplate);
        }
    }

    // =====================================================
    // 6.2 REMAINING REQUESTS SCENARIOS
    // =====================================================

    @Test
    public void testRemainingRequests() {
        // RR-01: Remaining count when key doesn't exist
        long remainingFirst = rateLimiterService.remaining(testKey, 5);
        Assertions.assertEquals(5, remainingFirst);

        // Increment count
        rateLimiterService.checkOrThrow(testKey, 5, 60);

        // RR-02: Remaining count decrements correctly
        long remainingSecond = rateLimiterService.remaining(testKey, 5);
        Assertions.assertEquals(4, remainingSecond);

        // RR-03: Remaining count is 0 when limit reached
        for (int i = 0; i < 4; i++) {
            rateLimiterService.checkOrThrow(testKey, 5, 60);
        }
        Assertions.assertEquals(0, rateLimiterService.remaining(testKey, 5));
    }

    // =====================================================
    // 6.3 RESET OPERATIONS SCENARIOS
    // =====================================================

    @Test
    public void testResetOperations() {
        // RS-01: Consume limit completely
        rateLimiterService.checkOrThrow(testKey, 1, 60);
        Assertions.assertThrows(ResponseStatusException.class, () -> {
            rateLimiterService.checkOrThrow(testKey, 1, 60);
        });

        // RS-02: Reset the key
        rateLimiterService.reset(testKey);

        // RS-03: Request allowed again
        Assertions.assertDoesNotThrow(() -> rateLimiterService.checkOrThrow(testKey, 1, 60));
    }
}
