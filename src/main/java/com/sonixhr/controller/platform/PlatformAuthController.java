package com.sonixhr.controller.platform;

import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.RefreshTokenRequest;
import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.PlatformTokenBlacklistService;
import com.sonixhr.security.RateLimiterService;
import com.sonixhr.security.HashingService;
import com.sonixhr.service.platform.PlatformAuthService;
import com.sonixhr.service.platform.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformAuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformAuthService platformAuthService;
    private final PlatformUserService platformUserService;
    private final JwtService jwtService;
    private final PlatformTokenBlacklistService tokenBlacklistService;
    private final RateLimiterService rateLimiterService;
    private final HashingService hashingService;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String email = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        String hashedEmail = hashingService.hashEmail(email);

        log.info("Login attempt for platform user: {}, IP: {}, User-Agent: {}", 
            request.getEmail(), ip, userAgent != null ? userAgent : "unknown");

        rateLimiterService.checkOrThrow("login:ip:" + ip, 10, 60);
        rateLimiterService.checkOrThrow("login:email:" + hashedEmail, 5, 60);

        try {
            LoginResponse response = platformAuthService.login(request.getEmail(), request.getPassword());
            
            // Reset rate limits on successful login
            rateLimiterService.reset("login:ip:" + ip);
            rateLimiterService.reset("login:email:" + hashedEmail);
            
            log.info("Successful login for platform user: {}, IP: {}", request.getEmail(), ip);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Login failed for user: {}, IP: {}", request.getEmail(), ip);
            throw e;
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal PlatformUser user,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        log.info("Logout request for platform user: {}, IP: {}", user.getEmail(), ip);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.blacklistToken(authHeader.substring(7));
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("refresh:ip:" + ip, 20, 60);

        log.info("Token refresh request from IP: {}", ip);
        LoginResponse response = platformAuthService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @Valid @RequestBody ActivationRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("activate:ip:" + ip, 5, 60);

        log.info("Activating platform user from IP: {}", ip);

        PlatformUser user = platformUserService.activateUser(
                request.getToken(),
                request.getPassword(),
                request.getConfirmPassword()
        );

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        log.info("Platform user activated successfully: {}, IP: {}", user.getEmail(), ip);

        // Return minimal response
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", "Account activated successfully");
        response.put("accessToken", tokenPair.getAccessToken());
        response.put("refreshToken", tokenPair.getRefreshToken());
        response.put("tokenType", "Bearer");
        response.put("expiresIn", tokenPair.getExpiresIn());
        response.put("userType", "PLATFORM");
        response.put("userId", user.getId());
        response.put("email", user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestParam String email,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("forgot:ip:" + ip, 3, 600);

        // Email-based rate limiting to prevent enumeration
        String hashedEmail = hashingService.hashEmail(email);
        rateLimiterService.checkOrThrow("forgot:email:" + hashedEmail, 2, 3600);

        // Random delay to prevent timing attacks
        try {
            Thread.sleep(500 + SECURE_RANDOM.nextInt(500));
        } catch (InterruptedException e) {
            log.error("Thread was interrupted during login delay", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Authentication delay was interrupted", e);
        }

        platformUserService.forgotPassword(email);
        return ResponseEntity.ok(Map.of(
                "message", "If the email exists, a password reset link has been sent.",
                "status", "success"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("reset:ip:" + ip, 5, 600);

        platformUserService.resetPasswordWithToken(
            request.getToken(), 
            request.getNewPassword(), 
            request.getConfirmPassword()
        );
        
        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully",
                "status", "success"
        ));
    }

    @GetMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken(
            @RequestParam String token,
            HttpServletRequest httpRequest) {

        // ✅ Add rate limiting for token verification
        rateLimiterService.checkOrThrow("verify-token:ip:" + resolveClientIp(httpRequest), 20, 60);

        if (tokenBlacklistService.isBlacklisted(token)) {
            return ResponseEntity.ok(Map.of("valid", false, "error", "Token is invalid or expired"));
        }

        try {
            boolean isValid = jwtService.validateToken(token);
            if (!isValid) {
                return ResponseEntity.ok(Map.of("valid", false, "error", "Token is invalid or expired"));
            }

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "userType", jwtService.extractUserType(token),
                    "email", jwtService.extractUsername(token)
            ));
        } catch (Exception e) {
            log.warn("Token verification failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("valid", false, "error", "Token is invalid or expired"));
        }
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }


}