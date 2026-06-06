package com.sonixhr.controller.tenant;

import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.dto.tenant.SubdomainCheckResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class TenantRegistrationController {

    private final TenantRegistrationService registrationService;
    private final ActivationTokenService activationTokenService;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * Check if a subdomain is available for registration
     */
    @GetMapping("/check-subdomain")
    public ResponseEntity<SubdomainCheckResponse> checkSubdomain(@RequestParam String subdomain) {
        log.debug("Checking subdomain availability: {}", subdomain);

        if (subdomain == null || subdomain.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SubdomainCheckResponse(false, "Subdomain is required"));
        }

        boolean available = !tenantRepository.existsBySubdomain(subdomain.toLowerCase());
        String message = available ? "Subdomain is available" : "Subdomain already taken";

        return ResponseEntity.ok(new SubdomainCheckResponse(available, message));
    }

    /**
     * Check if an email is already registered
     */
    @GetMapping("/check-email")
    public ResponseEntity<SubdomainCheckResponse> checkEmail(@RequestParam String email) {
        log.debug("Checking email availability: {}", email);

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SubdomainCheckResponse(false, "Email is required"));
        }

        boolean available = !employeeRepository.existsByEmail(email.toLowerCase());
        String message = available ? "Email is available" : "Email already registered";

        return ResponseEntity.ok(new SubdomainCheckResponse(available, message));
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

        // Validate token exists
        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new BusinessException("Activation token is required");
        }

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        // Validate password strength
        validatePasswordStrength(request.getNewPassword());

        // Set password and activate account
        activationTokenService.setPassword(request.getToken(), request.getNewPassword());

        log.info("Tenant admin activated successfully with token: {}", request.getToken());

        return ResponseEntity.ok().build();
    }

    /**
     * Validate password strength
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Password must contain at least one number");
        }
        if (!password.matches(".*[@#$%^&+=!].*")) {
            throw new BusinessException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }
}