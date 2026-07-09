package com.sonixhr.controller.tenant;

import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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



    /**
     * Check if an email is already registered
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        log.debug("Checking email availability: {}", email);

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("available", false, "message", "Email is required"));
        }

        boolean available = !employeeRepository.existsByEmail(email.toLowerCase());
        String message = available ? "Email is available" : "Email already registered";

        return ResponseEntity.ok(Map.of("available", available, "message", message));
    }

    /**
     * Register a new tenant (self-service registration)
     */
    @PostMapping("/register")
    public ResponseEntity<TenantRegistrationResponse> register(@Valid @RequestBody TenantRegistrationRequest request) {
        log.info("Received tenant registration request for company: {}", request.getCompanyName());

        TenantRegistrationResponse response = registrationService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Activate tenant account using email verification token
     */
    @PostMapping("/set-password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        log.info("Setting password for tenant admin with token: {}", request.getToken());

        // @Valid enforces @NotBlank on token and @Pattern on newPassword — only passwords-match needs manual check
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        activationTokenService.setPassword(request.getToken(), request.getNewPassword());

        log.info("Tenant admin activated successfully with token: {}", request.getToken());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/debug-redis")
    public ResponseEntity<Map<String, Object>> debugRedis() {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            result.put("redisTemplateClass", activationTokenService.getRedisTemplateClass());
            result.put("redisTest", activationTokenService.testRedis());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("stackTrace", org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
        }
        return ResponseEntity.ok(result);
    }
}