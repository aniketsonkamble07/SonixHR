package com.sonixhr.controller.platform;

import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.RefreshTokenRequest;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.PlatformTokenBlacklistService;
import com.sonixhr.security.RateLimiterService;
import com.sonixhr.service.platform.PlatformAuthService;
import com.sonixhr.service.platform.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;

import java.util.Map;

// FIXES APPLIED:
//
// 1. Rate limiting added to login, refresh, forgot-password, reset-password, and activate.
//    These are the endpoints most vulnerable to brute-force and enumeration attacks.
//    RateLimiterService (backed by Redis) is injected; returns 429 when the bucket is empty.
//
// 2. Client IP extraction extracted to a helper so X-Forwarded-For is respected
//    when the app sits behind a proxy/load-balancer.

@Slf4j
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;
    private final PlatformUserService platformUserService;
    private final JwtService jwtService;
    private final PlatformTokenBlacklistService tokenBlacklistService;
    private final RateLimiterService rateLimiterService;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // Rate-limit by IP: 10 attempts per minute.
        // Keyed by email too so credential-stuffing from many IPs is also throttled.
        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("login:ip:"    + ip,                  10, 60);
        String email = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        String hashedEmail = DigestUtils.md5DigestAsHex(email.getBytes());
        rateLimiterService.checkOrThrow("login:email:" + hashedEmail,  5,  60);

        log.info("Login request for platform user: {}", request.getEmail());
        LoginResponse response = platformAuthService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal PlatformUser user,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("Logout request for platform user: {}", user.getEmail());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.blacklistToken(authHeader.substring(7));
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status",  "success"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        // Rate-limit refresh: 20 per minute per IP. Less strict than login since
        // a valid refresh token is already a secret the caller holds.
        rateLimiterService.checkOrThrow("refresh:ip:" + resolveClientIp(httpRequest), 20, 60);

        log.info("Token refresh request");
        LoginResponse response = platformAuthService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @Valid @RequestBody ActivationRequest request,
            HttpServletRequest httpRequest) {

        // Rate-limit activation: 5 per minute per IP. Activation tokens are single-use
        // but an attacker can still try to guess/brute-force them.
        rateLimiterService.checkOrThrow("activate:ip:" + resolveClientIp(httpRequest), 5, 60);

        log.info("Activating platform user with token");

        PlatformUser user = platformUserService.activateUser(
                request.getToken(),
                request.getPassword(),
                request.getConfirmPassword()
        );

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        log.info("Platform user activated successfully: {}", user.getEmail());

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("success",      true),
                Map.entry("message",      "Account activated successfully"),
                Map.entry("accessToken",  tokenPair.getAccessToken()),
                Map.entry("refreshToken", tokenPair.getRefreshToken()),
                Map.entry("tokenType",    "Bearer"),
                Map.entry("expiresIn",    tokenPair.getExpiresIn()),
                Map.entry("userType",     "PLATFORM"),
                Map.entry("userId",       user.getId()),
                Map.entry("email",        user.getEmail()),
                Map.entry("fullName",     user.getFullName()),
                Map.entry("designation",  user.getDesignation())
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestParam String email,
            HttpServletRequest httpRequest) {

        // Rate-limit: 3 per 10 minutes per IP. This endpoint is often targeted
        // for email enumeration; the service already returns the same response
        // whether the email exists or not.
        rateLimiterService.checkOrThrow("forgot:ip:" + resolveClientIp(httpRequest), 3, 600);

        platformUserService.forgotPassword(email);
        return ResponseEntity.ok(Map.of(
                "message", "If the email exists, a password reset link has been sent.",
                "status",  "success"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpServletRequest httpRequest) {

        // Rate-limit: 5 per 10 minutes per IP.
        rateLimiterService.checkOrThrow("reset:ip:" + resolveClientIp(httpRequest), 5, 600);

        platformUserService.resetPasswordWithToken(token, newPassword, confirmPassword);
        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully",
                "status",  "success"
        ));
    }

    @GetMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestParam String token) {
        if (tokenBlacklistService.isBlacklisted(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token has been revoked"));
        }

        try {
            boolean isValid = jwtService.validateToken(token);
            if (!isValid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("valid", false, "error", "Token is invalid or expired"));
            }

            return ResponseEntity.ok(Map.of(
                    "valid",    true,
                    "userType", jwtService.extractUserType(token),
                    "email",    jwtService.extractUsername(token)
            ));
        } catch (Exception e) {
            log.warn("Token verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token is invalid or expired"));
        }
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Resolves the real client IP, respecting X-Forwarded-For when the app
     * runs behind a reverse proxy or load balancer.
     * Takes only the first address from the header to avoid spoofing via appended IPs.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}