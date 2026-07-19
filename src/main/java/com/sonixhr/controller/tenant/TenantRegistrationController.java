package com.sonixhr.controller.tenant;

import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import com.sonixhr.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class TenantRegistrationController {

    private final TenantRegistrationService registrationService;
    private final ActivationTokenService activationTokenService;
    private final EmployeeRepository employeeRepository;
    private final RateLimiterService rateLimiterService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @GetMapping("/plans")
    public ResponseEntity<List<com.sonixhr.dto.platform.SubscriptionPlanDTO>> getPublicPlans() {
        log.info("REST request to list public active subscription plans");
        List<com.sonixhr.entity.platform.SubscriptionPlan> plans = subscriptionPlanRepository.findAllActivePlans();
        List<com.sonixhr.dto.platform.SubscriptionPlanDTO> dtos = plans.stream()
                .filter(p -> p.isActive())
                .map(p -> com.sonixhr.dto.platform.SubscriptionPlanDTO.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .name(p.getName())
                        .description(p.getDescription())
                        .price(p.getPrice())
                        .validityMonths(p.getValidityMonths())
                        .isActive(p.isActive())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Value("${app.debug.redis-endpoint-enabled:false}")
    private boolean redisDebugEnabled;

    @Value("${app.debug.key:}")
    private String debugKey;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(
            @RequestParam String email,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);

        // Rate limit by IP
        try {
            rateLimiterService.checkOrThrow("email-check:" + clientIp, 10, 60);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many requests. Please try again later."));
        }

        // Validate email
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is required"));
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!isValidEmailFormat(normalizedEmail)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid email format"));
        }

        // Check existence
        boolean available = !employeeRepository.existsByEmail(normalizedEmail);

        // ✅ Log for monitoring but don't reveal to client
        if (log.isDebugEnabled()) {
            log.debug("Email check - IP: {}, email: {}, available: {}",
                    clientIp, maskEmail(normalizedEmail), available);
        }

        // ✅ Always return the same generic response
        return ResponseEntity.ok(Map.of(
                "message", "If this email is available, you will receive further instructions."));
    }

    @PostMapping("/register")
    public ResponseEntity<TenantRegistrationResponse> register(
            @Valid @RequestBody TenantRegistrationRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String companyName = request.getCompanyName();

        log.info("Tenant registration request from IP: {}, company: {}", clientIp, companyName);

        // Rate limit registration
        try {
            rateLimiterService.checkOrThrow("register:" + clientIp, 3, 3600);
        } catch (BusinessException e) {
            throw new BusinessException("Too many registration attempts. Please try again later.");
        }

        TenantRegistrationResponse response = registrationService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/set-password")
    public ResponseEntity<Void> setPassword(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Rate limit by IP
        try {
            rateLimiterService.checkOrThrow("set-password:" + clientIp, 5, 60);
        } catch (BusinessException e) {
            throw new BusinessException("Too many activation attempts. Please try again later.");
        }

        // Log token prefix only (NOT full token)
        String tokenPrefix = request.getToken() != null && request.getToken().length() > 8
                ? request.getToken().substring(0, 8) + "..."
                : "null";
        log.info("Set password request from IP: {}, token prefix: {}", clientIp, tokenPrefix);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        try {
            activationTokenService.setPassword(request.getToken(), request.getNewPassword());
            log.info("Password set successfully for token prefix: {}", tokenPrefix);
        } catch (BusinessException e) {
            log.warn("Failed set password attempt from IP: {}, token prefix: {}, error: {}",
                    clientIp, tokenPrefix, e.getMessage());
            throw e;
        }

        return ResponseEntity.ok().build();
    }

    // ✅ Protected debug endpoint - NO STACK TRACE EXPOSURE
    @GetMapping("/debug-redis")
    public ResponseEntity<Map<String, Object>> debugRedis(
            @RequestHeader(value = "X-Debug-Key", required = false) String providedKey) {

        if (!redisDebugEnabled) {
            return ResponseEntity.notFound().build();
        }

        if (debugKey != null && !debugKey.isEmpty() && !debugKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> result = new java.util.HashMap<>();
        try {
            result.put("redisTemplateClass", activationTokenService.getRedisTemplateClass());
            result.put("redisTest", activationTokenService.testRedis());
            result.put("success", true);
        } catch (Throwable e) {
            result.put("success", false);
            result.put("error", "Redis connection error. Check server logs for details.");
            // ✅ Log to server logs only
            log.error("Redis debug endpoint error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(result);
    }

    // ==============================
    // HELPER METHODS
    // ==============================

    private String getClientIp(HttpServletRequest request) {
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

    private boolean isValidEmailFormat(String email) {
        // ✅ Use a proper email validator
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 5)
            return "***";
        String[] parts = email.split("@");
        if (parts.length != 2)
            return "***";
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "*@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }
}