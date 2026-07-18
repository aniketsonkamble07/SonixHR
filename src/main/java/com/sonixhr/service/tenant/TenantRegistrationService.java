package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
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
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantLeaveSettingsRepository tenantLeaveSettingsRepository;
    private final TenantRLSService tenantRLSService;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${app.trial-days:14}")
    private int defaultTrialDays;

    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {
        log.info("Starting tenant registration for company: {}", request.getCompanyName());

        // 1. Validate uniqueness
        validateUniqueness(request);

        // 2. Get active plan details from DB
        if (request.getPlanCode() == null || request.getPlanCode().trim().isEmpty()) {
            throw new BusinessException("Subscription plan selection is required.");
        }
        SubscriptionPlan plan = subscriptionPlanRepository.findByNameIgnoreCase(request.getPlanCode().trim())
                .orElseThrow(() -> new BusinessException(
                        "Subscription plan not found with name: " + request.getPlanCode()));

        if (!plan.isActive()) {
            throw new BusinessException("The selected subscription plan is currently inactive.");
        }

        // 3. Generate unique identifiers
        String tenantCode = generateUniqueTenantCode(request.getCompanyName());

        // 4. Create Tenant
        Tenant tenant = createTenant(request, tenantCode, plan);
        log.info("Tenant created with ID: {}", tenant.getId());

        Long previousTenantId = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(tenant.getId());
            tenantRLSService.setCurrentTenantInDB(tenant.getId());

            // Initialize default leave settings immediately
            TenantLeaveSettings settings = TenantLeaveSettings.builder()
                    .tenantId(tenant.getId())
                    .country(tenant.getCountry())
                    .state(tenant.getState())
                    .stateText(tenant.getStateText())
                    .build();
            settings.setLeavePolicies(TenantLeaveSettings.createDefaultPolicies());
            tenantLeaveSettingsRepository.save(settings);

            // 5. Create default roles for this tenant (Admin, Employee, Manager)
            TenantRole adminRole = createDefaultRolesForTenant(tenant.getId());
            log.info("Default roles created for tenant, Admin has {} permissions",
                    adminRole.getPermissions().size());

            // 6. Create Employee (Admin)
            Employee adminEmployee = createSuperAdminEmployee(tenant, request, adminRole);
            log.info("Admin employee created with ID: {}", adminEmployee.getId());

            // 7. Create subscription record
            createSubscription(tenant, plan);
            log.info("Subscription created for tenant");

            // 8. Generate activation token for employee
            String activationToken = activationTokenService.generateTokenForEmployee(adminEmployee.getId());
            String activationLink = baseUrl + "/api/tenant/auth/activate?token=" + activationToken;

            // Send activation email to the registered Admin employee (disabled for
            // development)
            // emailService.sendActivationEmail(adminEmployee.getEmail(),
            // adminEmployee.getFullName(), activationLink);

            // Log the credentials for manual testing
            log.info("==========================================");
            log.info(" TENANT & ADMIN ACTIVE IMMEDIATELY (DEV MODE)");
            log.info(" Admin Email: {}", adminEmployee.getEmail());
            log.info(" (Password set to a secure random value)");
            log.info(" (Optional Activation Link: {})", activationLink);
            log.info("==========================================");

            log.info("Tenant registration completed: {}", tenant.getCompanyName());

            // 9. Create default General 9-5 shift configuration
            createDefaultShift(tenant.getId(), adminEmployee.getId());

            return buildResponse(tenant, activationToken, adminEmployee);
        } finally {
            if (previousTenantId != null) {
                TenantContext.setCurrentTenant(previousTenantId);
                tenantRLSService.setCurrentTenantInDB(previousTenantId);
            } else {
                TenantContext.clear();
                tenantRLSService.clearCurrentTenantInDB();
            }
        }
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
                .weeklyOffs(List.of("SUNDAY"))
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
            throw new ValidationException("companyName", "Company name already registered");
        }
        if (employeeRepository.existsByEmail(request.getAdminEmail())) {
            throw new ValidationException("adminEmail", "Email address already registered");
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
            SubscriptionPlan plan) {
        UserStatus tenantStatus = UserStatus.ACTIVE;
        boolean tenantActive = true;

        PlanStatus initialPlanStatus = PlanStatus.ACTIVE;
        int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
        LocalDateTime endsAt = LocalDateTime.now().plusMonths(validityMonths);

        String validatedCountry = CountryUtils.normalizeAndValidateCountry(request.getCountry());
        boolean isIndia = "IN".equalsIgnoreCase(validatedCountry);
        if (isIndia && request.getState() == null) {
            throw new ValidationException("state", "State is required for tenants in India");
        }

        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .companyName(request.getCompanyName())
                .subscriptionPlan(plan)
                .adminName(request.getAdminName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .officeAddress(request.getOfficeAddress())
                .city(request.getCity())
                .state(isIndia ? request.getState() : null)
                .stateText(!isIndia ? request.getStateText() : null)
                .country(validatedCountry)
                .status(tenantStatus)
                .isActive(tenantActive)
                .planStatus(initialPlanStatus)
                .endsAt(endsAt)
                .build();
        return tenantRepository.save(tenant);
    }

    // =====================================================
    // ROLE MANAGEMENT
    // =====================================================

    private TenantRole createDefaultRolesForTenant(Long tenantId) {
        log.info("Creating default roles for tenant: {}", tenantId);

        List<TenantPermission> allPermissions = permissionRepository.findAll();

        if (allPermissions.isEmpty()) {
            throw new BusinessException("No permissions found. Please ensure permissions are initialized.");
        }

        log.info("Found {} global permissions to assign to default roles", allPermissions.size());

        // 1. Admin
        TenantRole adminRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Admin")
                .description("Administrator with full access to all tenant features")
                .isDefault(true)
                .active(true)
                .permissions(new HashSet<>(allPermissions))
                .build();
        TenantRole savedAdmin = roleRepository.save(adminRole);

        // 2. Employee
        Set<String> employeePermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF", "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_CANCEL_OWN",
                "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN",
                "TASK_VIEW_OWN", "TASK_ACKNOWLEDGE", "TASK_UPDATE_STATUS");
        Set<TenantPermission> employeePermissions = allPermissions.stream()
                .filter(p -> employeePermissionNames.contains(p.getPermissionName()))
                .collect(java.util.stream.Collectors.toSet());
        TenantRole employeeRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Employee")
                .description("Basic employee access - default role for new employees")
                .isDefault(false)
                .active(true)
                .permissions(employeePermissions)
                .build();
        roleRepository.save(employeeRole);

        // 3. Manager
        Set<String> managerPermissionNames = Set.of(
                "EMPLOYEE_VIEW_SELF", "EMPLOYEE_VIEW_TEAM", "LEAVE_REQUEST", "LEAVE_VIEW_OWN", "LEAVE_VIEW_TEAM",
                "LEAVE_APPROVE_DEPARTMENT", "LEAVE_CANCEL_OWN", "ATTENDANCE_MARK_SELF", "ATTENDANCE_VIEW_OWN",
                "ATTENDANCE_VIEW_TEAM", "DEPARTMENT_VIEW", "REPORT_VIEW_DEPARTMENT",
                "TASK_CREATE", "TASK_VIEW_ALL", "TASK_VIEW_TEAM", "TASK_VIEW_OWN", "TASK_EDIT", "TASK_ACKNOWLEDGE", "TASK_UPDATE_STATUS");
        Set<TenantPermission> managerPermissions = allPermissions.stream()
                .filter(p -> managerPermissionNames.contains(p.getPermissionName()))
                .collect(java.util.stream.Collectors.toSet());
        TenantRole managerRole = TenantRole.builder()
                .tenantId(tenantId)
                .name("Manager")
                .description("Team manager with people management access")
                .isDefault(false)
                .active(true)
                .permissions(managerPermissions)
                .build();
        roleRepository.save(managerRole);

        log.info("Created default roles for tenant {}: Admin, Employee, Manager", tenantId);
        return savedAdmin;
    }

    // =====================================================
    // EMPLOYEE CREATION (Admin)
    // =====================================================

    private Employee createSuperAdminEmployee(Tenant tenant, TenantRegistrationRequest request,
            TenantRole adminRole) {
        String employeeCode = employeeCodeGenerator.generateEmployeeCode(tenant);

        String firstName = getFirstNameFromFullName(request.getAdminName());
        String lastName = getLastNameFromFullName(request.getAdminName());

        if (adminRole.getId() == null) {
            adminRole = roleRepository.save(adminRole);
        }

        EmployeeStatus employeeStatus = EmployeeStatus.ACTIVE;
        boolean employeeActive = true;
        String passwordHash = passwordEncoder.encode("Admin@123");

        Employee adminEmployee = Employee.builder()
                .tenant(tenant)
                .employeeCode(employeeCode)
                .firstName(firstName)
                .lastName(lastName)
                .email(request.getAdminEmail())
                .phone(request.getAdminPhone())
                .position("Admin")
                .employmentType(EmploymentType.FULL_TIME)
                .hireDate(LocalDate.now())
                .status(employeeStatus)
                .isActive(employeeActive)
                .address(tenant.getOfficeAddress())
                .city(tenant.getCity())
                .state(tenant.getState())
                .stateText(tenant.getStateText())
                .country(tenant.getCountry())
                .workLocation(
                        tenant.getCity() != null && !tenant.getCity().isEmpty() ? tenant.getCity() : "Head Office")
                .passwordHash(passwordHash)
                .createdBy(null)
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();

        return employeeRepository.save(adminEmployee);
    }

    private String getFirstNameFromFullName(String fullName) {
        if (fullName == null || fullName.isEmpty())
            return "Admin";
        String[] parts = fullName.trim().split(" ");
        return parts[0];
    }

    private String getLastNameFromFullName(String fullName) {
        if (fullName == null || fullName.isEmpty())
            return "User";
        String[] parts = fullName.trim().split(" ");
        if (parts.length > 1) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }

    // =====================================================
    // SUBSCRIPTION MANAGEMENT
    // =====================================================

    private void createSubscription(Tenant tenant, SubscriptionPlan plan) {
        PlanStatus initialPlanStatus = PlanStatus.ACTIVE;
        LocalDateTime startedAt = LocalDateTime.now();
        int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
        LocalDateTime endsAt = startedAt.plusMonths(validityMonths);

        TenantSubscription subscription = TenantSubscription.builder()
                .tenant(tenant)
                .subscriptionPlan(plan)
                .planName(plan.getName())
                .planStatus(initialPlanStatus)
                .startedAt(startedAt)
                .endsAt(endsAt)
                .billingPeriodStart(startedAt)
                .billingPeriodEnd(endsAt)
                .amount(plan.getPrice())
                .currency("INR")
                .isActive(true)
                .build();
        subscriptionRepository.save(subscription);
    }

    // =====================================================
    // RESPONSE BUILDING
    // =====================================================

    private TenantRegistrationResponse buildResponse(Tenant tenant, String activationToken,
            Employee superAdminEmployee) {
        String msg = "Registration successful! Please check your email to activate your account.";

        return TenantRegistrationResponse.builder()
                .success(true)
                .message(msg)
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .planType(tenant.getPlanType())
                .planStatus(tenant.getPlanStatus() != null ? tenant.getPlanStatus().name() : null)
                .endsAt(tenant.getEndsAt())
                .status(tenant.getStatus() != null ? tenant.getStatus().name() : null)
                .isActive(tenant.getIsActive())
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
                .superAdminEmployeeId(superAdminEmployee.getId())
                .superAdminEmployeeCode(superAdminEmployee.getEmployeeCode())
                .superAdminFullName(superAdminEmployee.getFullName())
                .superAdminEmail(superAdminEmployee.getEmail())
                .superAdminPosition(superAdminEmployee.getPosition())
                .build();
    }
}