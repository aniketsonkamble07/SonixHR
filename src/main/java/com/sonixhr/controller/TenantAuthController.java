package com.sonixhr.controller;

import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.TokenBlacklistService;
import com.sonixhr.security.TokenPair;
import com.sonixhr.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.sonixhr.exceptions.BusinessException;
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

    public TenantAuthController(
            @Qualifier("tenantAuthenticationManager") AuthenticationManager tenantAuthenticationManager,
            JwtService jwtService,
            EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            TokenBlacklistService tokenBlacklistService) {
        this.tenantAuthenticationManager = tenantAuthenticationManager;
        this.jwtService = jwtService;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for tenant user: {}", request.getEmail());

        try {
            Authentication auth = tenantAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            Employee employee = (Employee) auth.getPrincipal();

            // Update last login details
            employee.setLastLoginAt(java.time.LocalDateTime.now());
            employeeRepository.save(employee);

            TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

            log.info("Tenant user logged in successfully: {}", employee.getEmail());

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType("Bearer")
                    .expiresIn(tokenPair.getExpiresIn())
                    .email(employee.getEmail())
                    .fullName(employee.getFullName())
                    .build());

        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getEmail());
            throw new TenantAuthException("Invalid email or password", e);
        } catch (DisabledException | LockedException e) {
            log.warn("Login rejected for disabled/locked user: {}", request.getEmail());
            throw new TenantAuthException("Account is disabled or locked", e);
        } catch (Exception e) {
            log.error("Login error for user {}: {}", request.getEmail(), e.getMessage());
            throw new TenantAuthException("Login failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal Employee employee,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("Logout request for tenant user: {}", employee != null ? employee.getEmail() : "unknown");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklistToken(token);
            log.info("Token blacklisted for user: {}", employee != null ? employee.getEmail() : "unknown");
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        log.info("Token refresh request for tenant");

        try {
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                throw new TokenValidationException("Token has been revoked");
            }

            if (jwtService.validateToken(refreshToken) && jwtService.isRefreshToken(refreshToken)) {
                String username = jwtService.extractUsername(refreshToken);
                Employee employee = employeeRepository.findByEmail(username)
                        .orElseThrow(() -> new TenantAuthException("User not found"));

                if (!employee.isActive()) {
                    log.warn("Token refresh rejected for non-active tenant user: {}", username);
                    throw new TenantAuthException("Account is not active");
                }

                TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

                return ResponseEntity.ok(LoginResponse.builder()
                        .accessToken(tokenPair.getAccessToken())
                        .refreshToken(tokenPair.getRefreshToken())
                        .tokenType("Bearer")
                        .expiresIn(tokenPair.getExpiresIn())
                        .email(employee.getEmail())
                        .fullName(employee.getFullName())
                        .build());
            }
            throw new TokenValidationException("Invalid or expired refresh token");
        } catch (TenantAuthException | TokenValidationException e) {
            log.error("Refresh token error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Refresh token error: {}", e.getMessage());
            throw new TokenValidationException("Failed to refresh token: " + e.getMessage(), e);
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateUser(@Valid @RequestBody ActivationRequest request) {
        log.info("Activating tenant user with token: {}", request.getToken());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        Employee employee = employeeService.activateEmployee(
                request.getToken(),
                request.getPassword(),
                request.getConfirmPassword()
        );

        TokenPair tokenPair = jwtService.generateEmployeeTokenPair(employee);

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

        log.info("Tenant user activated successfully: {}", employee.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam String email) {
        log.info("Forgot password request for tenant email: {}", email);

        employeeService.forgotPassword(email);

        return ResponseEntity.ok(Map.of(
                "message", "If the email exists, a password reset link has been sent.",
                "status", "success"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword) {

        log.info("Reset password request with token: {}", token);

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        employeeService.resetPasswordWithToken(token, newPassword, confirmPassword);

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
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestParam String token) {
        log.debug("Verifying tenant token");

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
            Long tenantId = null;

            if (isValid) {
                userType = jwtService.extractUserType(token);
                email = jwtService.extractUsername(token);
                tenantId = jwtService.extractTenantIdAsLong(token);
            }

            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "userType", userType != null ? userType : "unknown",
                    "email", email != null ? email : "unknown",
                    "tenantId", tenantId != null ? tenantId : "unknown"
            ));
        } catch (Exception e) {
            log.error("Token verification error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", e.getMessage()
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
}