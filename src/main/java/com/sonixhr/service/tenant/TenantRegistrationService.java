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
import com.sonixhr.enums.UserStatus;
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
import com.sonixhr.service.attendance.ShiftConfigurationService;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final ActivationTokenService activationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final ShiftConfigurationService shiftConfigurationService;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${app.trial-days:14}")
    private int defaultTrialDays;

    @Value("${app.bypass-activation:false}")
    private boolean bypassActivation;

    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {
        log.info("Starting tenant registration for company: {}", request.getCompanyName());

        // 1. Validate uniqueness
        validateUniqueness(request);

        // 2. Get plan details
        PlanType planType = PlanType.fromCode(request.getPlanType());

        // 3. Generate unique identifiers
        String tenantCode = generateUniqueTenantCode(request.getCompanyName());

        // 4. Create Tenant
        Tenant tenant = createTenant(request, tenantCode, planType);
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
        String activationLink = baseUrl + "/api/tenant/employee/auth/activate?token=" + activationToken;

        // Log the activation link for manual testing
        log.info("==========================================");
        log.info(" ACTIVATION LINK: {}", activationLink);
        log.info(" Admin Email: {}", superAdminEmployee.getEmail());
        log.info("  Temporary Password: Admin@123");
        log.info("==========================================");

        log.info("Tenant registration completed: {}", tenant.getCompanyName());

        // 9. Create default General 9-5 shift configuration
        createDefaultShift(tenant.getId(), superAdminEmployee.getId());

        return buildResponse(tenant, activationToken, superAdminEmployee);
    }

    private void createDefaultShift(Long tenantId, Long adminEmployeeId) {
        log.info("Creating default shift configuration for tenant: {}", tenantId);

        ShiftConfigurationRequestDTO shiftRequest = ShiftConfigurationRequestDTO.builder()
                .shiftName("General 9-5")
                .shiftCode("GENERAL_9-5")
                .shiftDescription("Default general shift from 9:00 AM to 5:00 PM")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .breakDurationMinutes(60)
                .minBreakMinutes(30)
                .maxBreakMinutes(90)
                .lateGraceMinutes(15)
                .earlyExitGraceMinutes(15)
                .checkinBufferBefore(60)
                .checkoutBufferAfter(60)
                .fullDayHours(8.0)
                .halfDayHours(4.0)
                .quarterDayHours(2.0)
                .allowOvertime(true)
                .overtimeMultiplier(1.5)
                .overtimeThresholdMinutes(30)
                .maxOvertimeHoursPerDay(4.0)
                .weeklyOffs(List.of("SATURDAY", "SUNDAY"))
                .alternateWeekOff(false)
                .effectiveFrom(LocalDate.now())
                .build();

        Long previousTenantId = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(tenantId);
            shiftConfigurationService.createShiftConfiguration(shiftRequest, tenantId, adminEmployeeId);
            log.info("Default shift 'General 9-5' created successfully for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to create default shift for tenant: " + tenantId, e);
            throw new BusinessException("Failed to initialize default shift configuration: " + e.getMessage());
        } finally {
            if (previousTenantId != null) {
                TenantContext.setCurrentTenant(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    private void validateUniqueness(TenantRegistrationRequest request) {
        if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
            throw new DuplicateResourceException("Company name already exists: " + request.getCompanyName());
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

    private Tenant createTenant(TenantRegistrationRequest request, String tenantCode,
                                PlanType planType) {
        UserStatus tenantStatus = bypassActivation ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
        boolean tenantActive = bypassActivation;
        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .companyName(request.getCompanyName())
                .planType(planType)
                .maxEmployees(planType.getMaxEmployees())
                .adminName(request.getAdminName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .status(tenantStatus)
                .isActive(tenantActive)
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

        List<TenantPermission> allPermissions = permissionRepository.findAll();

        if (allPermissions.isEmpty()) {
            throw new BusinessException("No permissions found. Please ensure permissions are initialized.");
        }

        log.info("Found {} global permissions to assign to Super Admin role", allPermissions.size());

        TenantRole tenantRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Super Admin")
                .description("Super Administrator with full access to all tenant features")
                .isDefault(true)
                .permissions(new HashSet<>(allPermissions))
                .build();

        TenantRole savedRole = roleRepository.save(tenantRole);
        log.info(" Created Super Admin role for tenant {} with ID: {} and {} permissions",
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

        if (superAdminRole.getId() == null) {
            superAdminRole = roleRepository.save(superAdminRole);
        }

        EmployeeStatus employeeStatus = bypassActivation ? EmployeeStatus.ACTIVE : EmployeeStatus.PROBATION;
        boolean employeeActive = bypassActivation;
        String passwordHash = bypassActivation ? passwordEncoder.encode("Admin@123") : passwordEncoder.encode(java.util.UUID.randomUUID().toString());

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
                .status(employeeStatus)
                .isActive(employeeActive)
                .workLocation("Head Office")
                .passwordHash(passwordHash)
                .createdBy(null)
                .roles(new HashSet<>(Set.of(superAdminRole)))
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
        String msg = bypassActivation ? 
                "Registration successful! Account is active and password is set to Admin@123." :
                "Registration successful! Please check your email to activate your account.";

        return TenantRegistrationResponse.builder()
                .success(true)
                .message(msg)
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .planType(tenant.getPlanType() != null ? tenant.getPlanType().getCode() : null)
                .planStatus(tenant.getPlanStatus())
                .trialEndsAt(tenant.getTrialEndsAt())
                .status(tenant.getStatus() != null ? tenant.getStatus().name() : null)
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