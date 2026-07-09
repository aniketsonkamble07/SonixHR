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

    @Value("${app.debug.redis-endpoint-enabled:false}")
    private boolean redisDebugEnabled;

    @Value("${app.debug.key:}")
    private String debugKey;

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(
            @RequestParam String email,
            HttpServletRequest request) {

        // Rate limit by IP
        String clientIp = getClientIp(request);
        try {
            rateLimiterService.checkOrThrow("email-check:" + clientIp, 10, 60);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many requests. Please try again later."));
        }

        // Normalize and validate email
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("available", false, "message", "Email is required"));
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!isValidEmailFormat(normalizedEmail)) {
            return ResponseEntity.badRequest()
                .body(Map.of("available", false, "message", "Invalid email format"));
        }

        boolean available = !employeeRepository.existsByEmail(normalizedEmail);

        // Generic response to prevent email enumeration
        return ResponseEntity.ok(Map.of(
            "available", available,
            "message", "Email check completed"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<TenantRegistrationResponse> register(
            @Valid @RequestBody TenantRegistrationRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        log.info("Tenant registration request from IP: {}, company: {}", 
            clientIp, request.getCompanyName());

        // Rate limit registration
        try {
            rateLimiterService.checkOrThrow("register:" + clientIp, 3, 3600); // 3 per hour
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

    // Protected debug endpoint
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
            result.put("error", e.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            result.put("stackTrace", sw.toString());
        }
        return ResponseEntity.ok(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isValidEmailFormat(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}