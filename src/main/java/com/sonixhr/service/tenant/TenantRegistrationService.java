package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.BillingCycle;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.PlanType;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.DuplicateResourceException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.employee.EmployeeCodeGenerator;
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
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final ActivationTokenService activationTokenService;
    private final WelcomeTenantEmailService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final EmployeeCodeGenerator employeeCodeGenerator;

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

        // 5. Create Super Admin role for this tenant (with all global permissions)
        TenantRole superAdminRole = createSuperAdminRoleForTenant(tenant.getId());
        log.info("Super Admin role created for tenant with {} permissions", superAdminRole.getPermissions().size());

        // 6. Create Employee (Super Admin)
        Employee superAdminEmployee = createSuperAdminEmployee(tenant, request, superAdminRole);
        log.info("Super Admin employee created with ID: {}", superAdminEmployee.getId());

        // 7. Create subscription record
        createSubscription(tenant, planType);
        log.info("Subscription created for tenant");

        // 8. Generate activation token for employee
        String activationToken = activationTokenService.generateTokenForEmployee(superAdminEmployee.getId());
        log.info("Activation token generated for employee");

        log.info("Tenant registration completed: {}", tenant.getCompanyName());

        return buildResponse(tenant, activationToken, superAdminEmployee);
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    private void validateUniqueness(TenantRegistrationRequest request) {
        if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
            throw new DuplicateResourceException("Company name already exists: " + request.getCompanyName());
        }
        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new DuplicateResourceException("Subdomain already taken: " + request.getSubdomain());
        }
        if (employeeRepository.existsByEmail(request.getAdminEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getAdminEmail());
        }
    }

    // =====================================================
    // TENANT CREATION
    // =====================================================

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
        return code.toUpperCase();
    }

    private String generateUniqueSubdomain(String requestedSubdomain) {
        String subdomain = requestedSubdomain.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (subdomain.isEmpty()) {
            subdomain = "company";
        }
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

    // =====================================================
    // ROLE MANAGEMENT
    // =====================================================

    private TenantRole createSuperAdminRoleForTenant(Long tenantId) {
        log.info("Creating Super Admin role for tenant: {}", tenantId);

        // Get all global permissions
        List<TenantPermission> allPermissions = permissionRepository.findAll();

        if (allPermissions.isEmpty()) {
            throw new BusinessException("No permissions found. Please ensure permissions are initialized.");
        }

        log.info("Found {} global permissions to assign to Super Admin role", allPermissions.size());

        // Create tenant-specific Super Admin role with ALL global permissions
        TenantRole tenantRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Super Admin")
                .description("Super Administrator with full access to all tenant features")
                .isDefault(false)
                .permissions(new HashSet<>(allPermissions))
                .build();

        TenantRole savedRole = roleRepository.save(tenantRole);
        log.info("✅ Created Super Admin role for tenant {} with ID: {} and {} permissions",
                tenantId, savedRole.getId(), savedRole.getPermissions().size());

        return savedRole;
    }

    // =====================================================
    // EMPLOYEE CREATION (Super Admin)
    // =====================================================

    private Employee createSuperAdminEmployee(Tenant tenant, TenantRegistrationRequest request, TenantRole superAdminRole) {
        String employeeCode = employeeCodeGenerator.generateEmployeeCode(tenant);

        String firstName = getFirstNameFromFullName(request.getAdminName());
        String lastName = getLastNameFromFullName(request.getAdminName());

        // ✅ First save the role if it's new
        if (superAdminRole.getId() == null) {
            superAdminRole = roleRepository.save(superAdminRole);
        }

        Employee superAdmin = Employee.builder()
                .tenant(tenant)
                .employeeCode(employeeCode)
                .firstName(firstName)
                .lastName(lastName)
                .email(request.getAdminEmail())
                .phone(request.getAdminPhone())
                .position("Super Admin")
                .employmentType(EmploymentType.FULL_TIME)
                .hireDate(LocalDate.now())
                .probationMonths(0)
                .status(EmployeeStatus.ACTIVE)
                .isActive(true)
                .workLocation("Head Office")
                .passwordHash(passwordEncoder.encode("Admin@1234"))
                .createdBy(1L)
                .roles(new HashSet<>(Set.of(superAdminRole)))  // ✅ Set roles during creation
                .build();

        return employeeRepository.save(superAdmin);
    }

    private String getFirstNameFromFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "Admin";
        String[] parts = fullName.trim().split(" ");
        return parts[0];
    }

    private String getLastNameFromFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "User";
        String[] parts = fullName.trim().split(" ");
        if (parts.length > 1) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }

    // =====================================================
    // SUBSCRIPTION MANAGEMENT
    // =====================================================

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

    // =====================================================
    // RESPONSE BUILDING
    // =====================================================

    private TenantRegistrationResponse buildResponse(Tenant tenant, String activationToken, Employee superAdminEmployee) {
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
                .superAdminEmployeeId(superAdminEmployee.getId())
                .superAdminEmployeeCode(superAdminEmployee.getEmployeeCode())
                .superAdminFullName(superAdminEmployee.getFullName())
                .superAdminEmail(superAdminEmployee.getEmail())
                .superAdminPosition(superAdminEmployee.getPosition())
                .build();
    }
}