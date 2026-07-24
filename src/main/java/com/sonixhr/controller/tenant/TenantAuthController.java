package com.sonixhr.controller.tenant;

import com.sonixhr.dto.*;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.TenantAuthException;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.RateLimiterService;
import com.sonixhr.service.tenant.TenantAuthService;
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

import java.util.Map;

import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.service.employee.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;

@Slf4j
@RestController
@RequestMapping("/api/tenant/auth")
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;
    private final RateLimiterService rateLimiterService;
    private final JwtService jwtService; // ✅ Added
    private final EmployeeService employeeService;

    // =====================================================
    // LOGIN
    // =====================================================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String email = request.getEmail();
        String clientIp = resolveClientIp(httpRequest);

        try {
            log.info("Login attempt for user: {} from IP: {}", email, clientIp);

            Employee employee = tenantAuthService.findEmployeeByEmail(email);
            if (employee == null) {
                log.warn("Login failed: User not found - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Invalid email or password")
                                .errorCode("AUTH_001")
                                .build());
            }

            // Check account status
            if (employee.getStatus() == EmployeeStatus.INACTIVE) {
                log.warn("Login failed: Account not activated - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message(
                                        "Account not activated. Please activate your account using the activation link sent to your email.")
                                .errorCode("AUTH_007")
                                .requiresActivation(true)
                                .email(employee.getEmail())
                                .fullName(employee.getFullName())
                                .build());
            }

            if (employee.getStatus() == EmployeeStatus.INVITED) {
                log.warn("Login failed: Account pending activation - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Account pending activation. Please check your email for the activation link.")
                                .errorCode("AUTH_008")
                                .requiresActivation(true)
                                .email(employee.getEmail())
                                .fullName(employee.getFullName())
                                .build());
            }

            if (employee.getStatus() == EmployeeStatus.RESIGNED) {
                log.warn("Login failed: Account resigned - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Your account has been resigned. Please contact your administrator.")
                                .errorCode("AUTH_009")
                                .build());
            }

            if (employee.getStatus() == EmployeeStatus.TERMINATED) {
                log.warn("Login failed: Account terminated - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Your account has been terminated. Please contact your administrator.")
                                .errorCode("AUTH_010")
                                .build());
            }

            if (employee.getStatus() == EmployeeStatus.ON_LEAVE) {
                log.warn("Login failed: Account on leave - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Your account is currently on leave. Please contact your administrator.")
                                .errorCode("AUTH_011")
                                .build());
            }

            if (employee.isAccountLocked()) {
                log.warn("Login failed: Account locked - {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Account is locked. Please contact your administrator.")
                                .errorCode("AUTH_003")
                                .build());
            }

            LoginResponse response = tenantAuthService.login(email, request.getPassword());

            if (response.isSuccess()) {
                log.info("Login successful for user: {} from IP: {}", email, clientIp);
                return ResponseEntity.ok(response);
            } else if (response.isRequiresPasswordChange()) {
                log.info("Password change required for user: {} from IP: {}", email, clientIp);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            } else {
                log.warn("Login failed for user: {} from IP: {} - {}", email, clientIp, response.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

        } catch (org.springframework.security.core.AuthenticationException e) {
            log.warn("Authentication failed for user: {} from IP: {} - {}", email, clientIp, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .errorCode("AUTH_001")
                            .build());
        } catch (TenantAuthException e) {
            log.warn("Login failed for user: {} from IP: {} - {}", email, clientIp, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .errorCode("AUTH_001")
                            .build());
        } catch (Exception e) {
            log.error("Unexpected error during login for user: {} - {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Login failed. Please try again later.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        log.info("Logout request from IP: {}", ip);

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                tenantAuthService.logout(token);
                log.info("Logout successful for token from IP: {}", ip);
            } else {
                log.warn("Logout request without token from IP: {}", ip);
            }

            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(Map.of(
                    "message", "Logout successful",
                    "status", "success"));

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Logout failed",
                            "status", "error",
                            "errorCode", "AUTH_500"));
        }
    }

    // =====================================================
    // CHANGE PASSWORD
    // =====================================================

    // TenantAuthController.java - Update change-password endpoint

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoginResponse> changePassword(
            @AuthenticationPrincipal Employee currentUser,
            @Valid @RequestBody ChangePasswordRequest request, // ✅ Use ChangePasswordRequest
            HttpServletRequest httpRequest) {

        String clientIp = resolveClientIp(httpRequest);
        log.info("Password change request for user: {} from IP: {}", currentUser.getEmail(), clientIp);

        try {
            LoginResponse response = tenantAuthService.changePassword(
                    currentUser,
                    request.getCurrentPassword(),
                    request.getNewPassword(),
                    request.getConfirmPassword(),
                    httpRequest);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Failed to change password for user: {} - {}", currentUser.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to change password. Please try again.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // ACTIVATION - Set Password with Auto-Login
    // =====================================================

    @PostMapping("/activate")
    public ResponseEntity<LoginResponse> activate(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = resolveClientIp(httpRequest);
        log.info("Activation request from IP: {}", clientIp);

        try {
            // ✅ Rate limiting
            try {
                rateLimiterService.checkOrThrow("activate:" + clientIp, 5, 60);
            } catch (BusinessException e) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Too many activation attempts. Please try again later.")
                                .errorCode("AUTH_014")
                                .build());
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Passwords do not match")
                                .errorCode("AUTH_005")
                                .build());
            }

            // ✅ Activate the account
            tenantAuthService.setPassword(
                    request.getToken(),
                    request.getNewPassword(),
                    request.getConfirmPassword(),
                    httpRequest);

            log.info("Account activated successfully from IP: {}", clientIp);

            // ✅ AUTO-LOGIN after activation
            String email = jwtService.extractUsername(request.getToken());
            LoginResponse loginResponse = tenantAuthService.login(email, request.getNewPassword());

            log.info("User auto-logged in after activation: {}, IP: {}", email, clientIp);

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

        } catch (BusinessException e) {
            log.warn("Activation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .errorCode("AUTH_006")
                            .build());
        } catch (Exception e) {
            log.error("Failed to activate account: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to activate account. Please try again.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // FORGOT PASSWORD - Request reset link
    // =====================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<LoginResponse> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            tenantAuthService.forgotPassword(request.getEmail(), httpRequest);
            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("If an account exists with this email, you will receive a password reset link.")
                    .build());
        } catch (Exception e) {
            log.error("Failed to process forgot password request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to process request. Please try again.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // RESET PASSWORD - Reset password with token
    // =====================================================

    @PostMapping("/reset-password")
    public ResponseEntity<LoginResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(LoginResponse.builder()
                                .success(false)
                                .message("Passwords do not match")
                                .errorCode("AUTH_005")
                                .build());
            }

            tenantAuthService.resetPassword(
                    request.getToken(),
                    request.getNewPassword(),
                    request.getConfirmPassword(),
                    httpRequest);

            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("Password reset successfully. Please login with your new password.")
                    .build());
        } catch (BusinessException e) {
            log.warn("Failed to reset password: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .errorCode("AUTH_006")
                            .build());
        } catch (Exception e) {
            log.error("Failed to reset password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to reset password. Please try again.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // RESEND ACTIVATION EMAIL
    // =====================================================

    @PostMapping("/resend-activation")
    public ResponseEntity<LoginResponse> resendActivation(
            @RequestBody @Valid ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            log.info("REST request to resend activation email for: {}", request.getEmail());

            Employee employee = tenantAuthService.findEmployeeByEmail(request.getEmail());
            if (employee == null) {
                // Return success for security (don't reveal if email exists)
                return ResponseEntity.ok(LoginResponse.builder()
                        .success(true)
                        .message("If an account exists with this email, a new activation link will be sent.")
                        .build());
            }

            // Check if employee is already active
            if (employee.getStatus() == EmployeeStatus.ACTIVE) {
                return ResponseEntity.ok(LoginResponse.builder()
                        .success(false)
                        .message("Account is already activated. Please login.")
                        .errorCode("AUTH_012")
                        .build());
            }

            // Check if employee already has a password
            if (employee.getPasswordHash() != null && !employee.getPasswordHash().isEmpty()) {
                // Update status to ACTIVE
                employee.setStatus(EmployeeStatus.ACTIVE);
                employee.setActive(true);
                return ResponseEntity.ok(LoginResponse.builder()
                        .success(false)
                        .message("Account is already activated. Please login.")
                        .errorCode("AUTH_012")
                        .build());
            }

            tenantAuthService.resendActivationEmail(request.getEmail(), httpRequest);

            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("A new activation link has been sent to your email.")
                    .build());

        } catch (BusinessException e) {
            log.warn("Failed to resend activation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .errorCode("AUTH_013")
                            .build());
        } catch (Exception e) {
            log.error("Failed to resend activation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to send activation email. Please try again later.")
                            .errorCode("AUTH_500")
                            .build());
        }
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        log.info("Token refresh request from IP: {}", ip);

        try {
            LoginResponse response = tenantAuthService.refresh(request.getRefreshToken());

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            log.error("Failed to refresh token from IP: {} - {}", ip, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder()
                            .success(false)
                            .message("Failed to refresh token. Please login again.")
                            .errorCode("AUTH_006")
                            .build());
        }
    }

    // =====================================================
    // GET CURRENT USER PROFILE (/me)
    // =====================================================

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated tenant employee", description = "Returns currently authenticated tenant employee profile details")
    public ResponseEntity<EmployeeResponse> getCurrentUser(@AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get current tenant user profile for email: {}", currentEmployee.getEmail());
        EmployeeResponse response = employeeService.getEmployeeById(currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}