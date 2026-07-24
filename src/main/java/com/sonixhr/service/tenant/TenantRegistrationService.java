package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.service.subscription.SubscriptionEventLogService;
import com.sonixhr.util.CountryUtils;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.TenantPermissionEnum;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ValidationException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.employee.EmployeeCodeGenerator;
import com.sonixhr.service.attendance.ShiftConfigurationService;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.security.TenantContext;
import com.sonixhr.security.TenantRLSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantLeaveSettingsRepository tenantLeaveSettingsRepository;
    private final TenantRLSService tenantRLSService;
    private final SubscriptionEventLogService subscriptionEventLogService;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    // =====================================================
    // PERMISSION SETS (Permission-Based)
    // =====================================================

    private static final Set<String> EMPLOYEE_PERMISSIONS = Set.of(
            TenantPermissionEnum.EMPLOYEE_VIEW_SELF.name(),
            TenantPermissionEnum.LEAVE_REQUEST.name(),
            TenantPermissionEnum.LEAVE_VIEW_OWN.name(),
            TenantPermissionEnum.LEAVE_CANCEL_OWN.name(),
            TenantPermissionEnum.ATTENDANCE_MARK_SELF.name(),
            TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name(),
            TenantPermissionEnum.TASK_VIEW_OWN.name(),
            TenantPermissionEnum.TASK_ACKNOWLEDGE.name(),
            TenantPermissionEnum.TASK_UPDATE_STATUS.name());

    private static final Set<String> MANAGER_PERMISSIONS = new HashSet<>();
    static {
        MANAGER_PERMISSIONS.addAll(EMPLOYEE_PERMISSIONS);
        MANAGER_PERMISSIONS.addAll(Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_APPROVE_DEPARTMENT.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_TEAM.name(),
                TenantPermissionEnum.DEPARTMENT_VIEW.name(),
                TenantPermissionEnum.REPORT_VIEW_DEPARTMENT.name(),
                TenantPermissionEnum.TASK_CREATE.name(),
                TenantPermissionEnum.TASK_VIEW_ALL.name(),
                TenantPermissionEnum.TASK_VIEW_TEAM.name(),
                TenantPermissionEnum.TASK_EDIT.name()));
    }

    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {
        log.info("Starting tenant registration for company: {}", request.getCompanyName());

        // 1. Validate uniqueness
        validateUniqueness(request);

        // 2. Get subscription plan
        SubscriptionPlan plan = getSubscriptionPlan(request.getPlanCode());
        log.info("Selected plan: {} (ID: {})", plan.getName(), plan.getId());

        // 3. Generate tenant code
        String tenantCode = generateUniqueTenantCode(request.getCompanyName());

        // 4. Create Tenant
        Tenant tenant = createTenant(request, tenantCode, plan);
        log.info("Tenant created with ID: {}", tenant.getId());

        Long previousTenantId = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(tenant.getId());
            tenantRLSService.setCurrentTenantInDB(tenant.getId());

            // 5. Initialize leave settings
            initializeLeaveSettings(tenant);

            // 6. Create default roles (Admin, Manager, Employee)
            TenantRole adminRole = createDefaultRolesForTenant(tenant.getId());

            // 7. Create Admin Employee (NOT Super Admin)
            Employee adminEmployee = createAdminEmployee(tenant, request, adminRole);
            log.info("Admin employee created with ID: {}", adminEmployee.getId());

            // 8. Create subscription record
            createSubscription(tenant, plan);

            // 9. Generate activation token
            String activationToken = activationTokenService.generateTokenForEmployee(adminEmployee.getId());
            String activationLink = baseUrl + "/api/tenant/auth/activate?token=" + activationToken;

            logAdminCredentials(adminEmployee, activationLink);

            // 10. Create default shift - IN SEPARATE TRANSACTION
            createDefaultShiftSeparateTransaction(tenant.getId(), adminEmployee.getId());

            return buildResponse(tenant, activationToken, adminEmployee);

        } finally {
            restoreTenantContext(previousTenantId);
        }
    }

    /**
     * Create default shift in a SEPARATE transaction to avoid rollback issues
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createDefaultShiftSeparateTransaction(Long tenantId, Long adminEmployeeId) {
        try {
            log.info("Creating default shift for tenant: {} (separate transaction)", tenantId);

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
                    // ✅ Use String directly instead of List
                    .weeklyOffs("SUNDAY")
                    .alternateWeekOff(false)
                    .effectiveFrom(LocalDate.now())
                    .build();

            shiftConfigurationService.createShiftConfiguration(shiftRequest, tenantId, adminEmployeeId);
            log.info("Default shift 'General 9-5' created successfully for tenant: {}", tenantId);

        } catch (Exception e) {
            // Log but don't throw - shift creation failure shouldn't break tenant
            // registration
            log.error("Failed to create default shift for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    private void validateUniqueness(TenantRegistrationRequest request) {
        if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
            throw new ValidationException("companyName", "Company name already registered");
        }
        if (employeeRepository.existsByEmail(request.getAdminEmail())) {
            throw new ValidationException("adminEmail", "Email address already registered");
        }
    }

    private SubscriptionPlan getSubscriptionPlan(String planCode) {
        if (planCode == null || planCode.trim().isEmpty()) {
            log.info("No plan code provided, using default plan");
            return subscriptionPlanRepository.findByNameIgnoreCase("BASIC_MONTHLY")
                    .orElseThrow(() -> new BusinessException("Default plan not found. Please contact support."));
        }

        String code = planCode.trim();
        log.info("Looking for plan with code: {}", code);

        Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findByCodeIgnoreCase(code);

        if (planOpt.isEmpty()) {
            planOpt = subscriptionPlanRepository.findByNameIgnoreCase(code);
        }

        if (planOpt.isEmpty()) {
            List<SubscriptionPlan> availablePlans = subscriptionPlanRepository.findAllActivePlans();
            throw new BusinessException("Subscription plan not found: " + code +
                    ". Available plans: " + availablePlans.stream()
                            .map(SubscriptionPlan::getCode)
                            .collect(Collectors.joining(", ")));
        }

        SubscriptionPlan plan = planOpt.get();

        if (!plan.isActive()) {
            throw new BusinessException("The selected subscription plan is currently inactive.");
        }

        log.info("Found plan: {} ({})", plan.getCode(), plan.getName());
        return plan;
    }

    // =====================================================
    // TENANT CREATION
    // =====================================================

    private String generateUniqueTenantCode(String companyName) {
        String baseCode = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(?:^-)|(?:-$)", "");
        String code = baseCode;
        int counter = 1;
        while (tenantRepository.existsByTenantCode(code)) {
            code = baseCode + "-" + counter;
            counter++;
        }
        return code.toUpperCase();
    }

    private Tenant createTenant(TenantRegistrationRequest request, String tenantCode,
            SubscriptionPlan plan) {
        String validatedCountry = CountryUtils.normalizeAndValidateCountry(request.getCountry());
        boolean isIndia = "IN".equalsIgnoreCase(validatedCountry);

        if (isIndia && request.getState() == null) {
            throw new ValidationException("state", "State is required for tenants in India");
        }

        int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
        LocalDateTime endsAt = LocalDateTime.now().plusMonths(validityMonths);

        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .companyName(request.getCompanyName())
                .companyEmail(request.getCompanyEmail() != null ? request.getCompanyEmail() : request.getAdminEmail())
                .subscriptionPlan(plan)
                .adminName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .officeAddress(request.getAddress())
                .city(request.getCity())
                .state(isIndia ? request.getState() : null)
                .stateText(!isIndia ? request.getStateText() : null)
                .country(validatedCountry)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .planStatus(PlanStatus.ACTIVE) // ✅ PlanStatus enum
                .endsAt(endsAt)
                .maxEmployees(plan.getMaxEmployees() != null ? plan.getMaxEmployees() : 100)
                .build();

        return tenantRepository.save(tenant);
    }

    // =====================================================
    // LEAVE SETTINGS
    // =====================================================

    private void initializeLeaveSettings(Tenant tenant) {
        TenantLeaveSettings settings = TenantLeaveSettings.builder()
                .tenantId(tenant.getId())
                .country(tenant.getCountry())
                .state(tenant.getState())
                .stateText(tenant.getStateText())
                .build();
        settings.setLeavePolicies(TenantLeaveSettings.createDefaultPolicies());
        tenantLeaveSettingsRepository.save(settings);
    }

    // =====================================================
    // ROLE MANAGEMENT (Permission-Based)
    // =====================================================

    private TenantRole createDefaultRolesForTenant(Long tenantId) {
        log.info("Creating default roles for tenant: {}", tenantId);

        List<TenantPermission> allPermissions = permissionRepository.findAll();

        if (allPermissions.isEmpty()) {
            throw new BusinessException("No permissions found. Please ensure permissions are initialized.");
        }

        // 1. ADMIN ROLE - ALL PERMISSIONS
        TenantRole adminRole = createAdminRole(tenantId, allPermissions);
        roleRepository.save(adminRole);

        // 2. MANAGER ROLE - MANAGER PERMISSIONS
        TenantRole managerRole = createManagerRole(tenantId, allPermissions);
        roleRepository.save(managerRole);

        // 3. EMPLOYEE ROLE - BASIC PERMISSIONS
        TenantRole employeeRole = createEmployeeRole(tenantId, allPermissions);
        roleRepository.save(employeeRole);

        log.info("Created default roles for tenant {}: Admin, Manager, Employee", tenantId);
        return adminRole;
    }

    private TenantRole createAdminRole(Long tenantId, List<TenantPermission> allPermissions) {
        return TenantRole.builder()
                .tenantId(tenantId)
                .name("Admin")
                .description("Administrator with full access to all tenant features")
                .isDefault(true)
                .active(true)
                .permissions(new HashSet<>(allPermissions))
                .build();
    }

    private TenantRole createManagerRole(Long tenantId, List<TenantPermission> allPermissions) {
        Set<TenantPermission> managerPermissions = allPermissions.stream()
                .filter(p -> MANAGER_PERMISSIONS.contains(p.getPermissionName()))
                .collect(Collectors.toSet());

        return TenantRole.builder()
                .tenantId(tenantId)
                .name("Manager")
                .description("Team manager with people management access")
                .isDefault(false)
                .active(true)
                .permissions(managerPermissions)
                .build();
    }

    private TenantRole createEmployeeRole(Long tenantId, List<TenantPermission> allPermissions) {
        Set<TenantPermission> employeePermissions = allPermissions.stream()
                .filter(p -> EMPLOYEE_PERMISSIONS.contains(p.getPermissionName()))
                .collect(Collectors.toSet());

        return TenantRole.builder()
                .tenantId(tenantId)
                .name("Employee")
                .description("Basic employee access - default role for new employees")
                .isDefault(false)
                .active(true)
                .permissions(employeePermissions)
                .build();
    }

    // =====================================================
    // EMPLOYEE CREATION - ADMIN (NOT SUPER ADMIN)
    // =====================================================

    private Employee createAdminEmployee(Tenant tenant, TenantRegistrationRequest request,
            TenantRole adminRole) {
        String employeeCode = employeeCodeGenerator.generateEmployeeCode(tenant);

        String firstName = request.getAdminFirstName() != null ? request.getAdminFirstName() : "Admin";
        String lastName = request.getAdminLastName() != null ? request.getAdminLastName() : "User";

        String passwordHash = passwordEncoder.encode("Admin@123");

        Employee adminEmployee = Employee.builder()
                .tenant(tenant)
                .employeeCode(employeeCode)
                .firstName(firstName)
                .lastName(lastName)
                .email(request.getAdminEmail())
                .phone(request.getAdminPhone())
                .position("Administrator")
                .employmentType(EmploymentType.FULL_TIME)
                .hireDate(LocalDate.now())
                .status(EmployeeStatus.ACTIVE)
                .isActive(true)
                .address(tenant.getOfficeAddress())
                .city(tenant.getCity())
                .state(tenant.getState())
                .stateText(tenant.getStateText())
                .country(tenant.getCountry())
                .workLocation(
                        tenant.getCity() != null && !tenant.getCity().isEmpty() ? tenant.getCity() : "Head Office")
                .passwordHash(passwordHash)
                .roles(new HashSet<>(Set.of(adminRole)))
                .mustChangePassword(false)
                .build();

        return employeeRepository.save(adminEmployee);
    }

    // =====================================================
    // SUBSCRIPTION MANAGEMENT
    // =====================================================

    private void createSubscription(Tenant tenant, SubscriptionPlan plan) {
        try {
            LocalDateTime startedAt = LocalDateTime.now();
            int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
            LocalDateTime endsAt = startedAt.plusMonths(validityMonths);

            TenantSubscription subscription = TenantSubscription.builder()
                    .tenant(tenant)
                    .subscriptionPlan(plan)
                    .planName(plan.getName())
                    .planStatus(PlanStatus.ACTIVE) // ✅ PlanStatus enum
                    .startedAt(startedAt)
                    .billingPeriodStart(startedAt)
                    .billingPeriodEnd(endsAt)
                    .amount(plan.getPrice())
                    .currency(plan.getCurrency() != null ? plan.getCurrency() : "USD")
                    .isActive(true)
                    .isCurrent(true)
                    .build();

            TenantSubscription saved = subscriptionRepository.save(subscription);

            // ✅ CORRECT: Pass PlanStatus enums, NOT Strings
            subscriptionEventLogService.recordEvent(
                    tenant,
                    saved,
                    null, // previousStatus (PlanStatus)
                    PlanStatus.ACTIVE, // newStatus (PlanStatus) - NOT .name()
                    com.sonixhr.enums.TriggerSource.SYSTEM,
                    null,
                    "Initial subscription created with plan: " + plan.getName());

        } catch (Exception e) {
            log.error("Failed to create subscription for tenant {}: {}", tenant.getId(), e.getMessage());
            throw new BusinessException("Failed to create subscription: " + e.getMessage());
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private void restoreTenantContext(Long previousTenantId) {
        if (previousTenantId != null) {
            TenantContext.setCurrentTenant(previousTenantId);
            tenantRLSService.setCurrentTenantInDB(previousTenantId);
        } else {
            TenantContext.clear();
            tenantRLSService.clearCurrentTenantInDB();
        }
    }

    private void logAdminCredentials(Employee adminEmployee, String activationLink) {
        log.info("==========================================");
        log.info(" TENANT ADMIN CREATED");
        log.info(" Role: Admin (NOT Super Admin)");
        log.info(" Admin Email: {}", adminEmployee.getEmail());
        log.info(" Password: Admin@123");
        log.info(" Activation Link: {}", activationLink);
        log.info("==========================================");
    }

    // =====================================================
    // RESPONSE BUILDING
    // =====================================================

    private TenantRegistrationResponse buildResponse(Tenant tenant, String activationToken,
            Employee adminEmployee) {
        return TenantRegistrationResponse.builder()
                .success(true)
                .message("Registration successful! Please check your email to activate your account.")
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .companyEmail(tenant.getCompanyEmail())
                .companyPhone(tenant.getAdminPhone())
                .postalCode(tenant.getPostalCode())
                .planType(tenant.getPlanType())
                .planStatus(tenant.getPlanStatus() != null ? tenant.getPlanStatus().name() : null)
                .endsAt(tenant.getEndsAt())
                .status(tenant.getStatus() != null ? tenant.getStatus().name() : null)
                .isActive(tenant.getIsActive() != null && tenant.getIsActive())
                .adminEmail(tenant.getAdminEmail())
                .adminName(tenant.getAdminName())
                .adminPhone(tenant.getAdminPhone())
                .officeAddress(tenant.getOfficeAddress())
                .city(tenant.getCity())
                .state(tenant.getState())
                .stateText(tenant.getStateText())
                .country(tenant.getCountry())
                .activationToken(activationToken)
                .activationTokenExpiry(LocalDateTime.now().plusHours(24))
                .createdAt(tenant.getCreatedAt())
                .adminEmployeeId(adminEmployee.getId())
                .adminEmployeeCode(adminEmployee.getEmployeeCode())
                .adminFullName(adminEmployee.getFullName())
                .adminPosition(adminEmployee.getPosition())
                .build();
    }
}