package com.sonixhr.controller;

import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.security.RateLimiterService;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.TokenBlacklistService;
import com.sonixhr.security.TokenPair;
import com.sonixhr.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import com.sonixhr.exceptions.TenantAuthException;
import com.sonixhr.exceptions.TokenValidationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.sonixhr.security.TenantContext;
import com.sonixhr.security.TenantRLSService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tenant/auth")
public class TenantAuthController {

    private final AuthenticationManager tenantAuthenticationManager;
    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final TenantRLSService tenantRLSService;
    private final RateLimiterService rateLimiterService;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    public TenantAuthController(
            @Qualifier("tenantAuthenticationManager") AuthenticationManager tenantAuthenticationManager,
            JwtService jwtService,
            EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            TokenBlacklistService tokenBlacklistService,
            ShiftConfigurationRepository shiftConfigurationRepository,
            TenantRLSService tenantRLSService,
            RateLimiterService rateLimiterService) {
        this.tenantAuthenticationManager = tenantAuthenticationManager;
        this.jwtService = jwtService;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.shiftConfigurationRepository = shiftConfigurationRepository;
        this.tenantRLSService = tenantRLSService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String email = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        String hashedEmail = DigestUtils.md5DigestAsHex(email.getBytes());

        log.info("Login attempt for tenant user: {}, IP: {}, User-Agent: {}", 
            request.getEmail(), ip, userAgent != null ? userAgent : "unknown");

        rateLimiterService.checkOrThrow("login:ip:" + ip, 10, 60);
        rateLimiterService.checkOrThrow("login:email:" + hashedEmail, 5, 60);

        try {
            Authentication auth = tenantAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            Employee employee = (Employee) auth.getPrincipal();

            // Update last login details
            employee.setLastLoginAt(java.time.LocalDateTime.now());
            if (employee.getShift() == null) {
                shiftConfigurationRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(employee.getTenantId())
                        .ifPresent(employee::setShift);
            }
            employeeRepository.save(employee);

            TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

            // Register active session & invalidate previous one of same clientType
            String finalClientType = clientType != null ? clientType : "WEB";
            tokenBlacklistService.registerActiveSession(employee.getId(), finalClientType, tokenPair.getAccessToken());

            // Reset rate limits on successful login
            rateLimiterService.reset("login:ip:" + ip);
            rateLimiterService.reset("login:email:" + hashedEmail);

            log.info("Successful login for tenant user: {}, IP: {}", employee.getEmail(), ip);

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType("Bearer")
                    .expiresIn(tokenPair.getExpiresIn())
                    .email(employee.getEmail())
                    .fullName(employee.getFullName())
                    .build());

        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}, IP: {}", request.getEmail(), ip);
            throw new TenantAuthException("Invalid email or password", e);
        } catch (DisabledException | LockedException e) {
            log.warn("Login rejected for disabled/locked user: {}, IP: {}", request.getEmail(), ip);
            throw new TenantAuthException("Account is disabled or locked", e);
        } catch (Exception e) {
            log.error("Login error for user {} from IP {}: {}", request.getEmail(), ip, e.getMessage());
            throw new TenantAuthException("Login failed. Please try again.", e);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal Employee employee,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        log.info("Logout request for tenant user: {}, IP: {}", employee != null ? employee.getEmail() : "unknown", ip);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklistToken(token);
            log.info("Token blacklisted for user: {}, IP: {}", employee != null ? employee.getEmail() : "unknown", ip);
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @RequestParam String refreshToken,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("refresh:ip:" + ip, 20, 60);

        log.info("Token refresh request for tenant from IP: {}", ip);

        try {
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                throw new TokenValidationException("Token has been revoked");
            }

            if (jwtService.validateRefreshToken(refreshToken)) {
                String username = jwtService.extractUsername(refreshToken);
                Long tenantId = jwtService.getTenantIdFromToken(refreshToken);
                if (tenantId != null) {
                    if (tokenBlacklistService.isTenantBlacklisted(tenantId)) {
                        throw new TokenValidationException("Tenant has been suspended");
                    }
                    TenantContext.setCurrentTenant(tenantId);
                    tenantRLSService.setCurrentTenantInDB(tenantId);
                }
                try {
                    Employee employee = employeeRepository.findByEmail(username)
                            .orElseThrow(() -> new TenantAuthException("User not found"));

                    if (!employee.isActive()) {
                        log.warn("Token refresh rejected for non-active tenant user: {}", username);
                        throw new TenantAuthException("Account is not active");
                    }

                    TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

                    // Register active session & invalidate previous one of same clientType
                    String finalClientType = clientType != null ? clientType : "WEB";
                    tokenBlacklistService.registerActiveSession(employee.getId(), finalClientType, tokenPair.getAccessToken());

                    return ResponseEntity.ok(LoginResponse.builder()
                            .accessToken(tokenPair.getAccessToken())
                            .refreshToken(tokenPair.getRefreshToken())
                            .tokenType("Bearer")
                            .expiresIn(tokenPair.getExpiresIn())
                            .email(employee.getEmail())
                            .fullName(employee.getFullName())
                            .build());
                } finally {
                    TenantContext.clear();
                    tenantRLSService.clearCurrentTenantInDB();
                }
            }
            throw new TokenValidationException("Invalid or expired refresh token");
        } catch (TenantAuthException | TokenValidationException e) {
            log.error("Refresh token error (auth/validation): {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Refresh token error (generic): {}", e.getMessage(), e);
            throw new TokenValidationException("Failed to refresh token. Please log in again.", e);
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @Valid @RequestBody ActivationRequest request,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("tenant:activate:ip:" + ip, 5, 60);

        String tokenPrefix = request.getToken() != null && request.getToken().length() > 8
                ? request.getToken().substring(0, 8) + "..."
                : "null";
        log.info("Activating tenant user from IP: {}, token prefix: {}", ip, tokenPrefix);

        Employee employee = employeeService.activateEmployee(
                request.getToken(),
                request.getPassword(),
                request.getConfirmPassword()
        );

        TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

        // Register active session & invalidate previous one of same clientType
        String finalClientType = clientType != null ? clientType : "WEB";
        tokenBlacklistService.registerActiveSession(employee.getId(), finalClientType, tokenPair.getAccessToken());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account activated successfully!");
        response.put("accessToken", tokenPair.getAccessToken());
        response.put("refreshToken", tokenPair.getRefreshToken());
        response.put("tokenType", "Bearer");
        response.put("expiresIn", tokenPair.getExpiresIn());
        response.put("userType", "EMPLOYEE");
        response.put("userId", employee.getId());
        response.put("email", employee.getEmail());
        response.put("fullName", employee.getFullName());
        response.put("tenantId", employee.getTenantId());

        log.info("Tenant user activated successfully: {}, IP: {}", employee.getEmail(), ip);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestParam String email,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("tenant:forgot:ip:" + ip, 3, 600);

        // Email-based rate limiting to prevent enumeration
        String hashedEmail = DigestUtils.md5DigestAsHex(email.toLowerCase().trim().getBytes());
        rateLimiterService.checkOrThrow("tenant:forgot:email:" + hashedEmail, 2, 3600);

        log.info("Forgot password request for tenant email from IP: {}", ip);

        // Random delay to prevent timing attacks
        try {
            Thread.sleep(500 + (long) (Math.random() * 500));
        } catch (InterruptedException ignored) {}

        employeeService.forgotPassword(email);

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
        rateLimiterService.checkOrThrow("tenant:reset:ip:" + ip, 5, 600);
        log.info("Reset password request with token from IP: {}", ip);

        employeeService.resetPasswordWithToken(request.getToken(), request.getNewPassword(), request.getConfirmPassword());

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully!",
                "status", "success"
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        log.info("Getting current tenant user info");

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "authenticated", false,
                    "message", "Not authenticated"
            ));
        }

        String email = authentication.getName();
        Employee employee = employeeRepository.findByEmail(email).orElse(null);

        if (employee == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "email", email,
                    "message", "User authenticated but not found in database"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("userType", "EMPLOYEE");
        response.put("id", employee.getId());
        response.put("email", employee.getEmail());
        response.put("fullName", employee.getFullName());
        response.put("tenantId", employee.getTenantId());
        response.put("status", employee.getStatus());
        response.put("isActive", employee.isActive());
        response.put("authorities", authentication.getAuthorities().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken(
            @RequestParam String token,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkOrThrow("tenant:verify-token:ip:" + ip, 20, 60);

        log.debug("Verifying tenant token from IP: {}", ip);

        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                return ResponseEntity.ok(Map.of(
                        "valid", false,
                        "error", "Token has been revoked"
                ));
            }

            boolean isValid = jwtService.validateToken(token);
            String userType = null;
            String email = null;

            if (isValid) {
                userType = jwtService.extractUserType(token);
                email = jwtService.extractUsername(token);
            }

            // Issue 5: Return minimal info (no tenantId exposed in response map)
            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "userType", userType != null ? userType : "unknown",
                    "email", email != null ? email : "unknown"
            ));
        } catch (Exception e) {
            log.error("Token verification error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", "Token verification failed"
            ));
        }
    }

    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(Authentication authentication) {
        log.debug("Testing tenant authentication - Authentication present: {}", authentication != null);

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "email", "null",
                    "authorities", "[]",
                    "message", "No authentication found"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("name", authentication.getName());
        response.put("authorities", authentication.getAuthorities().toString());

        Object principal = authentication.getPrincipal();
        if (principal instanceof Employee) {
            Employee employee = (Employee) principal;
            response.put("userType", "EMPLOYEE");
            response.put("email", employee.getEmail());
            response.put("fullName", employee.getFullName());
            response.put("tenantId", employee.getTenantId());
        }

        return ResponseEntity.ok(response);
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