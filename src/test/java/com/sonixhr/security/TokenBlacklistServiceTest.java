package com.sonixhr.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtService jwtService;

    @Mock
    private Claims mockClaims;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenBlacklistService, "blacklistEnabled", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void registerActiveSession_shouldRegisterNewSessionWhenNoPreviousSessionExists() {
        when(valueOperations.get("user:session:101:WEB")).thenReturn(null);

        when(jwtService.extractAllClaims("new-token")).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("new-jti");
        Date futureDate = new Date(System.currentTimeMillis() + 3600000); // 1 hour in future
        when(mockClaims.getExpiration()).thenReturn(futureDate);

        tokenBlacklistService.registerActiveSession(101L, "WEB", "new-token");

        // Verify new session is stored
        verify(valueOperations).set(eq("user:session:101:WEB"), contains("new-jti:"), anyLong(), eq(TimeUnit.MILLISECONDS));
        // Verify no blacklist operation occurred
        verify(valueOperations, never()).set(startsWith("token:blacklist:"), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void registerActiveSession_shouldBlacklistPreviousSessionWhenExists() {
        // Mock a previous active session: JTI is "old-jti", expiring 30 mins in future
        long oldExpiry = System.currentTimeMillis() + 1800000;
        when(valueOperations.get("user:session:101:WEB")).thenReturn("old-jti:" + oldExpiry);

        when(jwtService.extractAllClaims("new-token")).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("new-jti");
        Date futureDate = new Date(System.currentTimeMillis() + 3600000); // 1 hour in future
        when(mockClaims.getExpiration()).thenReturn(futureDate);

        tokenBlacklistService.registerActiveSession(101L, "WEB", "new-token");

        // Verify old JTI is blacklisted in Redis
        verify(valueOperations).set(eq("token:blacklist:old-jti"), eq("true"), anyLong(), eq(TimeUnit.MILLISECONDS));
        // Verify new session is stored
        verify(valueOperations).set(eq("user:session:101:WEB"), contains("new-jti:"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void registerActiveSession_shouldIsolateWebAndMobileSessions() {
        // Registering a MOBILE session should check "user:session:101:MOBILE"
        when(valueOperations.get("user:session:101:MOBILE")).thenReturn(null);

        when(jwtService.extractAllClaims("new-mobile-token")).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("mobile-jti");
        Date futureDate = new Date(System.currentTimeMillis() + 3600000);
        when(mockClaims.getExpiration()).thenReturn(futureDate);

        tokenBlacklistService.registerActiveSession(101L, "MOBILE", "new-mobile-token");

        // Verify mobile session key is used
        verify(valueOperations).set(eq("user:session:101:MOBILE"), contains("mobile-jti:"), anyLong(), eq(TimeUnit.MILLISECONDS));
        // Verify web session key "user:session:101:WEB" was not queried
        verify(valueOperations, never()).get("user:session:101:WEB");
    }
}
