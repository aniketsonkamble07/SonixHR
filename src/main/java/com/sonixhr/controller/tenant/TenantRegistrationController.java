package com.sonixhr.controller.tenant;

import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.dto.subscription.SubscriptionPlanDTO;
import com.sonixhr.entity.platform.SubscriptionPlan;
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
import org.springframework.jdbc.core.JdbcTemplate;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.debug.redis-endpoint-enabled:false}")
    private boolean redisDebugEnabled;

    @Value("${app.debug.key:}")
    private String debugKey;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    // =====================================================
    // PUBLIC ENDPOINTS
    // =====================================================

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getPublicPlans() {
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAllActivePlans();
        List<SubscriptionPlanDTO> dtos = plans.stream()
                .filter(SubscriptionPlan::isActive)
                .map(plan -> SubscriptionPlanDTO.builder()
                        .id(plan.getId())
                        .code(plan.getCode())
                        .name(plan.getName())
                        .description(plan.getDescription())
                        .price(plan.getPrice())
                        .validityMonths(plan.getValidityMonths())
                        .currency(plan.getCurrency())
                        .maxUsers(plan.getMaxUsers())
                        .maxEmployees(plan.getMaxEmployees())
                        .isActive(plan.isActive())
                        .isPublic(plan.getIsPublic())
                        .displayOrder(plan.getDisplayOrder())
                        .isCustom(plan.getIsCustom())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(
            @RequestParam String email,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);

        try {
            rateLimiterService.checkOrThrow("email-check:" + clientIp, 10, 60);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many requests. Please try again later."));
        }

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!isValidEmailFormat(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid email format"));
        }

        boolean available = !employeeRepository.existsByEmail(normalizedEmail);

        log.debug("Email check - IP: {}, available: {}", clientIp, available);

        return ResponseEntity.ok(Map.of(
                "message", "If this email is available, you will receive further instructions."));
    }

    @PostMapping("/register")
    public ResponseEntity<TenantRegistrationResponse> register(
            @Valid @RequestBody TenantRegistrationRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

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

        try {
            rateLimiterService.checkOrThrow("set-password:" + clientIp, 5, 60);
        } catch (BusinessException e) {
            throw new BusinessException("Too many activation attempts. Please try again later.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        activationTokenService.setPassword(request.getToken(), request.getNewPassword(), httpRequest);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // DEBUG ENDPOINTS (Protected)
    // =====================================================

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
            log.error("Redis debug endpoint error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/debug-db")
    public ResponseEntity<Map<String, Object>> debugDb() {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            result.put("employees", jdbcTemplate.queryForList("SELECT id, email, is_active, status, tenant_id FROM employees LIMIT 100"));
            result.put("tenants", jdbcTemplate.queryForList("SELECT id, company_name, tenant_code, status, is_active, data_status FROM tenants LIMIT 100"));
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

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
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}