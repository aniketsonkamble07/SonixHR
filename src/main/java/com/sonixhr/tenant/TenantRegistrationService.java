package com.sonixhr.tenant;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.entity.*;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.BillingCycle;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.PlanType;
import com.sonixhr.enums.UserType;
import com.sonixhr.exceptions.DuplicateResourceException;
import com.sonixhr.repository.*;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final WelcomeTenantEmailService notificationService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${app.trial-days:14}")
    private int defaultTrialDays;

    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {
        log.info("Starting tenant registration for company: {}", request.getCompanyName());

        // 1. Validate uniqueness
        validateUniqueness(request);

        // 2. Get plan details
        PlanType planType = PlanType.fromCode(request.getPlanType());

        // 3. Generate unique identifiers
        String tenantCode = generateUniqueTenantCode(request.getCompanyName());
        String subdomain = generateUniqueSubdomain(request.getSubdomain());

        // 4. Create Tenant
        Tenant tenant = createTenant(request, tenantCode, subdomain, planType);
        log.info("Tenant created with ID: {}", tenant.getId());

        // 5. Copy Super Admin role for this tenant
        Role superAdminRole = copySuperAdminRoleForTenant(tenant.getId());
        log.info("Super Admin role copied for tenant");

        // 6. Create admin user
        User adminUser = createAdminUser(tenant, request);
        log.info("Admin user created: {}", adminUser.getEmail());

        // 7. Assign Super Admin role to admin user
        assignSuperAdminRole(adminUser, superAdminRole);
        log.info("Super Admin role assigned to admin user");

        // 8. Create subscription record
        createSubscription(tenant, planType);
        log.info("Subscription created for tenant");

        // 9. Generate activation token
        String activationToken = generateActivationToken(adminUser);
        log.info("Activation token generated");

        // 10. Send welcome email
        sendWelcomeEmail(tenant, adminUser, activationToken, planType);
        log.info("Welcome email sent");

        log.info("Tenant registration completed: {}", tenant.getCompanyName());

        return buildResponse(tenant, activationToken);
    }

    private void validateUniqueness(TenantRegistrationRequest request) {
        if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
            throw new DuplicateResourceException("Company name already exists: " + request.getCompanyName());
        }
        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new DuplicateResourceException("Subdomain already taken: " + request.getSubdomain());
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getAdminEmail());
        }
    }

    private String generateUniqueTenantCode(String companyName) {
        String baseCode = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String code = baseCode;
        int counter = 1;
        while (tenantRepository.existsByTenantCode(code)) {
            code = baseCode + "-" + counter;
            counter++;
        }
        return code;
    }

    private String generateUniqueSubdomain(String requestedSubdomain) {
        String subdomain = requestedSubdomain.toLowerCase().replaceAll("[^a-z0-9]", "");
        String original = subdomain;
        int counter = 1;
        while (tenantRepository.existsBySubdomain(subdomain)) {
            subdomain = original + counter;
            counter++;
        }
        return subdomain;
    }

    private Tenant createTenant(TenantRegistrationRequest request, String tenantCode,
                                String subdomain, PlanType planType) {
        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .companyName(request.getCompanyName())
                .subdomain(subdomain)
                .planType(planType.getCode())
                .maxEmployees(planType.getMaxEmployees())
                .maxStorageMb(planType.getMaxStorageMb())
                .adminName(request.getAdminName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .status("active")
                .isActive(true)
                .planStatus("trial")
                .trialEndsAt(LocalDateTime.now().plusDays(defaultTrialDays))
                .build();
        return tenantRepository.save(tenant);
    }

    private Role copySuperAdminRoleForTenant(UUID tenantId) {
        Role templateRole = roleRepository.findByNameAndTenantIdIsNull("Super Admin")
                .orElseThrow(() -> new RuntimeException("Super Admin template role not found"));

        Role tenantRole = Role.builder()
                .tenantId(tenantId)
                .name(templateRole.getName())
                .description(templateRole.getDescription())
                .permissions(new HashSet<>(templateRole.getPermissions()))
                .build();

        return roleRepository.save(tenantRole);
    }


    private User createAdminUser(Tenant tenant, TenantRegistrationRequest request) {
        User adminUser = User.builder()
                .tenant(tenant)
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode("TEMPORARY_DISABLED"))
                .fullName(request.getAdminName())
                .isActive(false)
                .build();
        return userRepository.save(adminUser);
    }

    private void assignSuperAdminRole(User adminUser, Role superAdminRole) {
        adminUser.getRoles().add(superAdminRole);
        userRepository.save(adminUser);
    }

    // ✅ Fixed: Use proper tenant relationship
    private void createSubscription(Tenant tenant, PlanType planType) {
        TenantSubscription subscription = TenantSubscription.builder()
                .tenant(tenant)
                .planType(planType)
                .planName(planType.getDisplayName())
                .planStatus(PlanStatus.TRIAL)
                .maxEmployees(planType.getMaxEmployees())
                .maxStorageMb(planType.getMaxStorageMb())
                .trialStartedAt(LocalDateTime.now())
                .trialEndsAt(LocalDateTime.now().plusDays(defaultTrialDays))
                .startedAt(LocalDateTime.now())
                .endsAt(LocalDateTime.now().plusDays(defaultTrialDays))
                .amount(BigDecimal.valueOf(planType.getMonthlyPrice()))
                .currency("INR")
                .billingCycle(BillingCycle.MONTHLY)
                .isActive(true)
                .build();
        subscriptionRepository.save(subscription);
    }

    private String generateActivationToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .userType(UserType.TENANT)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        activationTokenRepository.save(token);
        return tokenValue;
    }

    private void sendWelcomeEmail(Tenant tenant, User user, String activationToken, PlanType planType) {
        notificationService.sendTenantWelcomeEmail(
                tenant.getAdminEmail(),
                tenant.getAdminName(),
                tenant.getCompanyName(),
                tenant.getSubdomain(),
                activationToken,
                planType.getDisplayName(),
                defaultTrialDays
        );
    }

    private TenantRegistrationResponse buildResponse(Tenant tenant, String activationToken) {
        return TenantRegistrationResponse.builder()
                .success(true)
                .message("Registration successful! Please check your email to activate your account.")
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .subdomain(tenant.getSubdomain())
                .planType(tenant.getPlanType())
                .planStatus(tenant.getPlanStatus())
                .trialEndsAt(tenant.getTrialEndsAt())
                .status(tenant.getStatus())
                .isActive(tenant.getIsActive())
                .adminEmail(tenant.getAdminEmail())
                .adminName(tenant.getAdminName())
                .adminPhone(tenant.getAdminPhone())
                .activationToken(activationToken)
                .activationTokenExpiry(LocalDateTime.now().plusHours(24))
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}