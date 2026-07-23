package com.sonixhr.controller.platform;

import com.sonixhr.dto.*;
import com.sonixhr.dto.platform.PlatformUserResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    // =====================================================
    // LOGIN
    // =====================================================

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

            rateLimiterService.reset("login:ip:" + ip);
            rateLimiterService.reset("login:email:" + hashedEmail);

            log.info("Successful login for platform user: {}, IP: {}", request.getEmail(), ip);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Login failed for user: {}, IP: {}", request.getEmail(), ip);
            throw e;
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal PlatformUser user,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        log.info("Logout request for platform user: {}, IP: {}", user.getEmail(), ip);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            platformAuthService.logout(authHeader.substring(7));
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

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

    // =====================================================
    // ACTIVATE USER
    // =====================================================

    @PostMapping("/activate")
    public ResponseEntity<LoginResponse> activateUser(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("activate:ip:" + ip, 5, 60);

        log.info("Activating platform user from IP: {}", ip);

        platformAuthService.setPassword(
                request.getToken(),
                request.getNewPassword(),
                request.getConfirmPassword(),
                httpRequest
        );

        log.info("Platform user activated successfully from IP: {}", ip);

        // Auto-login after activation
        String email = jwtService.extractUsername(request.getToken());
        LoginResponse loginResponse = platformAuthService.login(email, request.getNewPassword());

        log.info("Platform user auto-logged in after activation: {}, IP: {}", email, ip);

        return ResponseEntity.ok(LoginResponse.builder()
                .success(true)
                .message("Account activated and logged in successfully")
                .accessToken(loginResponse.getAccessToken())
                .refreshToken(loginResponse.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(loginResponse.getExpiresIn())
                .email(loginResponse.getEmail())
                .fullName(loginResponse.getFullName())
                .userId(loginResponse.getUserId())
                .build());
    }

    // =====================================================
    // FORGOT PASSWORD
    // =====================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String email = request.getEmail();
        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("forgot:ip:" + ip, 3, 600);

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

        platformAuthService.forgotPassword(email);
        return ResponseEntity.ok(Map.of(
                "message", "If the email exists, a password reset link has been sent.",
                "status", "success"
        ));
    }

    // =====================================================
    // RESET PASSWORD
    // =====================================================

    @PostMapping("/reset-password")
    public ResponseEntity<LoginResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("reset:ip:" + ip, 5, 600);

        log.info("Password reset request from IP: {}", ip);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .success(false)
                    .message("Passwords do not match")
                    .errorCode("AUTH_005")
                    .build());
        }

        platformAuthService.resetPassword(
                request.getToken(),
                request.getNewPassword(),
                request.getConfirmPassword(),
                httpRequest
        );

        // Auto-login after password reset
        String email = jwtService.extractUsername(request.getToken());
        LoginResponse loginResponse = platformAuthService.login(email, request.getNewPassword());

        return ResponseEntity.ok(LoginResponse.builder()
                .success(true)
                .message("Password reset successfully. You are now logged in.")
                .accessToken(loginResponse.getAccessToken())
                .refreshToken(loginResponse.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(loginResponse.getExpiresIn())
                .email(loginResponse.getEmail())
                .fullName(loginResponse.getFullName())
                .userId(loginResponse.getUserId())
                .build());
    }

    // =====================================================
    // VERIFY TOKEN
    // =====================================================

    @GetMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken(
            @RequestParam String token,
            HttpServletRequest httpRequest) {

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

    // =====================================================
    // CHANGE PASSWORD - ✅ FIXED: Using ChangePasswordRequest
    // =====================================================

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoginResponse> changePassword(
            @AuthenticationPrincipal PlatformUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("change-password:ip:" + ip, 5, 600);
        rateLimiterService.checkOrThrow("change-password:user:" + currentUser.getId(), 3, 3600);

        log.info("Password change request for platform user: {}, IP: {}", currentUser.getEmail(), ip);

        // ✅ Use ChangePasswordRequest with currentPassword
        LoginResponse response = platformAuthService.changePassword(
                currentUser,
                request.getCurrentPassword(),
                request.getNewPassword(),
                request.getConfirmPassword(),
                httpRequest
        );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET CURRENT USER
    // =====================================================

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlatformUserResponse> getCurrentUser(
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.debug("REST request to get current platform user");
        return ResponseEntity.ok(platformUserService.getUserById(currentUser.getId()));
    }

    // =====================================================
    // RESEND ACTIVATION
    // =====================================================

    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, String>> resendActivation(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String email = request.getEmail();
        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("resend-activation:ip:" + ip, 3, 600);

        log.info("REST request to resend activation email for: {}", email);
        platformUserService.resendActivationEmail(email);

        return ResponseEntity.ok(Map.of(
                "message", "A new activation link has been sent to your email.",
                "status", "success"
        ));
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

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