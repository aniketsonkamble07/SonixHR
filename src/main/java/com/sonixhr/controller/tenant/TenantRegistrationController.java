package com.sonixhr.controller.tenant;



import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.SetPasswordRequest;
import com.sonixhr.dto.tenant.SubdomainCheckResponse;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.UserRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.tenant.TenantRegistrationService;
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
    private final ActivationTokenService activationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ActivationTokenService activationTokenService;
    /**
     * Check if a subdomain is available for registration
     */
    @GetMapping("/check-subdomain")
    public ResponseEntity<SubdomainCheckResponse> checkSubdomain(@RequestParam String subdomain) {
        log.debug("Checking subdomain availability: {}", subdomain);
        boolean available = !tenantRepository.existsBySubdomain(subdomain);
        return ResponseEntity.ok(new SubdomainCheckResponse(available,
                available ? "Subdomain is available" : "Subdomain already taken"));
    }

    /**
     * Check if an email is already registered
     */
    @GetMapping("/check-email")
    public ResponseEntity<SubdomainCheckResponse> checkEmail(@RequestParam String email) {
        log.debug("Checking email availability: {}", email);
        boolean available = !userRepository.existsByEmail(email);
        return ResponseEntity.ok(new SubdomainCheckResponse(available,
                available ? "Email is available" : "Email already registered"));
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
        activationTokenService.setPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
}