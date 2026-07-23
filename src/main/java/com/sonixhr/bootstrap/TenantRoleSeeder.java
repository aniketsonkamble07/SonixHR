package com.sonixhr.bootstrap;

import com.sonixhr.entity.attendance.ShiftConfiguration;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.enums.TenantPermissionEnum;
import com.sonixhr.common.constant.AppConstants;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
@SuppressWarnings("null")
public class TenantRoleSeeder implements ApplicationRunner {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    private static final String LOCK_KEY = "bootstrap:tenant-role-seeder:lock";
    private static final int PAGE_SIZE = 100;

    // =====================================================
    // CONFIGURABLE PROPERTIES
    // =====================================================

    @Value("${app.seeder.shift.default.name:Default Shift}")
    private String defaultShiftName;

    @Value("${app.seeder.shift.default.code:DEFAULT}")
    private String defaultShiftCode;

    @Value("${app.seeder.shift.default.description:Standard working hours (9 AM - 5 PM)}")
    private String defaultShiftDescription;

    @Value("${app.seeder.shift.default.start-time:09:00}")
    private String defaultStartTime;

    @Value("${app.seeder.shift.default.end-time:17:00}")
    private String defaultEndTime;

    @Value("${app.seeder.shift.default.weekly-offs:SATURDAY,SUNDAY}")
    private String defaultWeeklyOffs;

    @Value("${app.seeder.shift.default.allow-overtime:true}")
    private Boolean defaultAllowOvertime;

    @Value("${app.seeder.shift.default.overtime-multiplier:1.5}")
    private Double defaultOvertimeMultiplier;

    @Value("${app.seeder.shift.default.overtime-threshold:30}")
    private Integer defaultOvertimeThreshold;

    @Value("${app.seeder.shift.default.max-overtime-hours:4.0}")
    private Double defaultMaxOvertimeHours;

    @Value("${app.seeder.shift.default.full-day-hours:8.0}")
    private Double defaultFullDayHours;

    @Value("${app.seeder.shift.default.half-day-hours:4.0}")
    private Double defaultHalfDayHours;

    @Value("${app.seeder.shift.default.quarter-day-hours:2.0}")
    private Double defaultQuarterDayHours;

    @Value("${app.seeder.shift.default.break-duration:60}")
    private Integer defaultBreakDuration;

    @Value("${app.seeder.shift.default.min-break:30}")
    private Integer defaultMinBreak;

    @Value("${app.seeder.shift.default.max-break:90}")
    private Integer defaultMaxBreak;

    @Value("${app.seeder.shift.default.late-grace:15}")
    private Integer defaultLateGrace;

    @Value("${app.seeder.shift.default.early-exit-grace:15}")
    private Integer defaultEarlyExitGrace;

    @Value("${app.seeder.shift.default.checkin-buffer:60}")
    private Integer defaultCheckinBuffer;

    @Value("${app.seeder.shift.default.checkout-buffer:60}")
    private Integer defaultCheckoutBuffer;

    @Value("${app.seeder.admin.default-password:Admin@123}")
    private String defaultAdminPassword;

    @Value("${app.seeder.admin.default-position:Administrator}")
    private String defaultAdminPosition;

    @Value("${app.seeder.admin.default-employment-type:FULL_TIME}")
    private String defaultAdminEmploymentType;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Boolean lockAcquired = null;
        try {
            lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "running", 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis unavailable for TenantRoleSeeder lock — proceeding without lock: {}", e.getMessage());
        }

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.info("TenantRoleSeeder lock held by another instance. Skipping to avoid duplicate seeding.");
            return;
        }

        try {
            log.info(AppConstants.DIVIDER);
            log.info("Tenant Role Seeder Started");
            log.info(AppConstants.DIVIDER);

            List<TenantPermission> allPermissions = permissionRepository.findAll();
            log.info("Found {} permissions", allPermissions.size());

            if (allPermissions.isEmpty()) {
                log.warn("No permissions found! Please ensure TenantPermissionSeeder runs first.");
                return;
            }

            int page = 0;
            List<Tenant> tenants;
            int totalProcessed = 0;

            do {
                tenants = tenantRepository.findAll(PageRequest.of(page++, PAGE_SIZE)).getContent();

                if (!tenants.isEmpty()) {
                    List<Long> tenantIds = tenants.stream()
                            .map(Tenant::getId)
                            .collect(Collectors.toList());

                    Map<Long, List<TenantRole>> rolesByTenant = getExistingRolesByTenant(tenantIds);
                    Map<Long, List<Employee>> employeesByTenant = getExistingEmployeesByTenant(tenantIds);

                    for (Tenant tenant : tenants) {
                        try {
                            log.debug("Processing tenant: {} (ID: {})", tenant.getCompanyName(), tenant.getId());

                            List<TenantRole> tenantRoles = rolesByTenant.getOrDefault(tenant.getId(), List.of());
                            List<Employee> tenantEmployees = employeesByTenant.getOrDefault(tenant.getId(), List.of());

                            // Seed roles for tenant
                            Set<TenantRole> createdRoles = createRolesForTenant(tenant.getId(), tenantRoles, allPermissions);

                            // Seed default shift for tenant (with proper transaction handling)
                            ShiftConfiguration defaultShift = createDefaultShiftForTenant(tenant.getId());

                            // Seed default admin user for tenant
                            createDefaultAdminForTenant(tenant, createdRoles, tenantEmployees, defaultShift);

                        } catch (Exception e) {
                            log.error("Error processing tenant {}: {}", tenant.getId(), e.getMessage(), e);
                            // Continue with next tenant
                        }
                    }

                    totalProcessed += tenants.size();
                }
            } while (tenants.size() == PAGE_SIZE);

            if (totalProcessed == 0) {
                log.warn("No tenants found — skipping role seeding.");
            } else {
                log.info("Seeded roles, admin users, and shifts for {} tenants", totalProcessed);
            }

            log.info(AppConstants.DIVIDER);
            log.info("Tenant Role Seeder Completed");
            log.info(AppConstants.DIVIDER);
        } finally {
            if (Boolean.TRUE.equals(lockAcquired)) {
                try {
                    redisTemplate.delete(LOCK_KEY);
                } catch (Exception e) {
                    log.warn("Could not release TenantRoleSeeder lock: {}", e.getMessage());
                }
            }
        }
    }

    // =====================================================
    // EXISTING DATA FETCHING
    // =====================================================

    private Map<Long, List<TenantRole>> getExistingRolesByTenant(List<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<TenantRole> existingRoles = roleRepository.findAllByTenantIdInWithPermissions(tenantIds);
            return existingRoles.stream()
                    .filter(r -> r.getTenantId() != null)
                    .collect(Collectors.groupingBy(TenantRole::getTenantId));
        } catch (Exception e) {
            log.warn("Failed to fetch existing roles: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, List<Employee>> getExistingEmployeesByTenant(List<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Employee> existingEmployees = employeeRepository.findAllByTenantIdIn(tenantIds);
            return existingEmployees.stream()
                    .collect(Collectors.groupingBy(Employee::getTenantId));
        } catch (Exception e) {
            log.warn("Failed to fetch existing employees: {}", e.getMessage());
            return Map.of();
        }
    }

    // =====================================================
    // ROLE CREATION
    // =====================================================

    private Set<TenantRole> createRolesForTenant(Long tenantId, List<TenantRole> tenantRoles,
                                                 List<TenantPermission> allPermissions) {
        Set<TenantRole> createdRoles = new HashSet<>();

        try {
            // Admin Role - ALL permissions
            TenantRole adminRole = createAdminRole(tenantId, tenantRoles, allPermissions);
            if (adminRole != null) createdRoles.add(adminRole);

            // HR Role - HR permissions
            TenantRole hrRole = createHRRole(tenantId, tenantRoles, allPermissions);
            if (hrRole != null) createdRoles.add(hrRole);

            // Manager Role - Manager permissions
            TenantRole managerRole = createManagerRole(tenantId, tenantRoles, allPermissions);
            if (managerRole != null) createdRoles.add(managerRole);

            // Employee Role - Employee permissions
            TenantRole employeeRole = createEmployeeRole(tenantId, tenantRoles, allPermissions);
            if (employeeRole != null) createdRoles.add(employeeRole);
        } catch (Exception e) {
            log.error("Error creating roles for tenant {}: {}", tenantId, e.getMessage());
        }

        return createdRoles;
    }

    private TenantRole createAdminRole(Long tenantId, List<TenantRole> tenantRoles,
                                       List<TenantPermission> allPermissions) {
        try {
            Optional<TenantRole> existingRole = tenantRoles.stream()
                    .filter(r -> "Admin".equalsIgnoreCase(r.getName()))
                    .findFirst();

            if (existingRole.isPresent()) {
                TenantRole adminRole = existingRole.get();
                if (adminRole.getPermissions() == null || adminRole.getPermissions().size() < allPermissions.size()) {
                    adminRole.setPermissions(new HashSet<>(allPermissions));
                    roleRepository.save(adminRole);
                    log.info("Synced permissions for existing Admin role for tenant: {}", tenantId);
                }
                return adminRole;
            }

            TenantRole adminRole = TenantRole.builder()
                    .tenantId(tenantId)
                    .name("Admin")
                    .description("Administrator with full access to all tenant features")
                    .isDefault(true)
                    .active(true)
                    .permissions(new HashSet<>(allPermissions))
                    .build();

            roleRepository.save(adminRole);
            log.info("Created Admin role for tenant {} with {} permissions", tenantId, allPermissions.size());
            return adminRole;
        } catch (Exception e) {
            log.error("Error creating Admin role for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private TenantRole createHRRole(Long tenantId, List<TenantRole> tenantRoles,
                                    List<TenantPermission> allPermissions) {
        try {
            Set<String> hrPermissionNames = getHRPermissionNames();

            Set<TenantPermission> hrPermissions = allPermissions.stream()
                    .filter(p -> hrPermissionNames.contains(p.getPermissionName()))
                    .collect(Collectors.toSet());

            Optional<TenantRole> existingRole = tenantRoles.stream()
                    .filter(r -> "HR".equalsIgnoreCase(r.getName()))
                    .findFirst();

            if (existingRole.isPresent()) {
                TenantRole hrRole = existingRole.get();
                if (hrRole.getPermissions() == null || !hrRole.getPermissions().containsAll(hrPermissions)) {
                    if (hrRole.getPermissions() == null) {
                        hrRole.setPermissions(new HashSet<>());
                    }
                    hrRole.getPermissions().addAll(hrPermissions);
                    roleRepository.save(hrRole);
                    log.info("Synced permissions for existing HR role for tenant: {}", tenantId);
                }
                return hrRole;
            }

            TenantRole hrRole = TenantRole.builder()
                    .tenantId(tenantId)
                    .name("HR")
                    .description("Human Resources with employee and leave management access")
                    .isDefault(false)
                    .active(true)
                    .permissions(hrPermissions)
                    .build();

            roleRepository.save(hrRole);
            log.info("Created HR role for tenant {} with {} permissions", tenantId, hrPermissions.size());
            return hrRole;
        } catch (Exception e) {
            log.error("Error creating HR role for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private TenantRole createManagerRole(Long tenantId, List<TenantRole> tenantRoles,
                                         List<TenantPermission> allPermissions) {
        try {
            Set<String> managerPermissionNames = getManagerPermissionNames();

            Set<TenantPermission> managerPermissions = allPermissions.stream()
                    .filter(p -> managerPermissionNames.contains(p.getPermissionName()))
                    .collect(Collectors.toSet());

            Optional<TenantRole> existingRole = tenantRoles.stream()
                    .filter(r -> "Manager".equalsIgnoreCase(r.getName()))
                    .findFirst();

            if (existingRole.isPresent()) {
                TenantRole managerRole = existingRole.get();
                if (managerRole.getPermissions() == null || !managerRole.getPermissions().containsAll(managerPermissions)) {
                    if (managerRole.getPermissions() == null) {
                        managerRole.setPermissions(new HashSet<>());
                    }
                    managerRole.getPermissions().addAll(managerPermissions);
                    roleRepository.save(managerRole);
                    log.info("Synced permissions for existing Manager role for tenant: {}", tenantId);
                }
                return managerRole;
            }

            TenantRole managerRole = TenantRole.builder()
                    .tenantId(tenantId)
                    .name("Manager")
                    .description("Team manager with people management access")
                    .isDefault(false)
                    .active(true)
                    .permissions(managerPermissions)
                    .build();

            roleRepository.save(managerRole);
            log.info("Created Manager role for tenant {} with {} permissions", tenantId, managerPermissions.size());
            return managerRole;
        } catch (Exception e) {
            log.error("Error creating Manager role for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private TenantRole createEmployeeRole(Long tenantId, List<TenantRole> tenantRoles,
                                          List<TenantPermission> allPermissions) {
        try {
            Set<String> employeePermissionNames = getEmployeePermissionNames();

            Set<TenantPermission> employeePermissions = allPermissions.stream()
                    .filter(p -> employeePermissionNames.contains(p.getPermissionName()))
                    .collect(Collectors.toSet());

            Optional<TenantRole> existingRole = tenantRoles.stream()
                    .filter(r -> "Employee".equalsIgnoreCase(r.getName()))
                    .findFirst();

            if (existingRole.isPresent()) {
                TenantRole employeeRole = existingRole.get();
                if (employeeRole.getPermissions() == null || !employeeRole.getPermissions().containsAll(employeePermissions)) {
                    if (employeeRole.getPermissions() == null) {
                        employeeRole.setPermissions(new HashSet<>());
                    }
                    employeeRole.getPermissions().addAll(employeePermissions);
                    roleRepository.save(employeeRole);
                    log.info("Synced permissions for existing Employee role for tenant: {}", tenantId);
                }
                return employeeRole;
            }

            TenantRole employeeRole = TenantRole.builder()
                    .tenantId(tenantId)
                    .name("Employee")
                    .description("Basic employee access - default role for new employees")
                    .isDefault(true)
                    .active(true)
                    .permissions(employeePermissions)
                    .build();

            roleRepository.save(employeeRole);
            log.info("Created Employee role for tenant {} with {} permissions", tenantId, employeePermissions.size());
            return employeeRole;
        } catch (Exception e) {
            log.error("Error creating Employee role for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    // =====================================================
    // PERMISSION NAME SETS
    // =====================================================

    private Set<String> getEmployeePermissionNames() {
        return Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_SELF.name(),
                TenantPermissionEnum.LEAVE_REQUEST.name(),
                TenantPermissionEnum.LEAVE_VIEW_OWN.name(),
                TenantPermissionEnum.LEAVE_CANCEL_OWN.name(),
                TenantPermissionEnum.ATTENDANCE_MARK_SELF.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_OWN.name(),
                TenantPermissionEnum.TASK_VIEW_OWN.name(),
                TenantPermissionEnum.TASK_ACKNOWLEDGE.name(),
                TenantPermissionEnum.TASK_UPDATE_STATUS.name()
        );
    }

    private Set<String> getManagerPermissionNames() {
        Set<String> permissions = new HashSet<>(getEmployeePermissionNames());
        permissions.addAll(Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_VIEW_TEAM.name(),
                TenantPermissionEnum.LEAVE_APPROVE_DEPARTMENT.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_TEAM.name(),
                TenantPermissionEnum.DEPARTMENT_VIEW.name(),
                TenantPermissionEnum.REPORT_VIEW_DEPARTMENT.name(),
                TenantPermissionEnum.TASK_CREATE.name(),
                TenantPermissionEnum.TASK_VIEW_ALL.name(),
                TenantPermissionEnum.TASK_VIEW_TEAM.name(),
                TenantPermissionEnum.TASK_EDIT.name()
        ));
        return permissions;
    }

    private Set<String> getHRPermissionNames() {
        Set<String> permissions = new HashSet<>(getManagerPermissionNames());
        permissions.addAll(Set.of(
                TenantPermissionEnum.EMPLOYEE_VIEW_ALL.name(),
                TenantPermissionEnum.EMPLOYEE_CREATE.name(),
                TenantPermissionEnum.EMPLOYEE_EDIT.name(),
                TenantPermissionEnum.EMPLOYEE_DELETE.name(),
                TenantPermissionEnum.EMPLOYEE_EXPORT.name(),
                TenantPermissionEnum.LEAVE_VIEW_ALL.name(),
                TenantPermissionEnum.LEAVE_APPROVE_ANY.name(),
                TenantPermissionEnum.LEAVE_CANCEL_ANY.name(),
                TenantPermissionEnum.ATTENDANCE_VIEW_ALL.name(),
                TenantPermissionEnum.ATTENDANCE_EDIT.name(),
                TenantPermissionEnum.ATTENDANCE_EXPORT.name(),
                TenantPermissionEnum.ROLE_VIEW.name(),
                TenantPermissionEnum.ROLE_ASSIGN.name(),
                TenantPermissionEnum.ROLE_VIEW_PERMISSIONS.name(),
                TenantPermissionEnum.DEPARTMENT_CREATE.name(),
                TenantPermissionEnum.DEPARTMENT_EDIT.name(),
                TenantPermissionEnum.DEPARTMENT_DELETE.name(),
                TenantPermissionEnum.REPORT_VIEW_COMPANY.name(),
                TenantPermissionEnum.REPORT_EXPORT.name(),
                TenantPermissionEnum.SETTINGS_VIEW.name(),
                TenantPermissionEnum.SETTINGS_EDIT.name()
        ));
        return permissions;
    }

    // =====================================================
    // DEFAULT ADMIN USER CREATION
    // =====================================================

    @Transactional
    protected void createDefaultAdminForTenant(Tenant tenant, Set<TenantRole> createdRoles,
                                               List<Employee> existingEmployees,
                                               ShiftConfiguration defaultShift) {
        try {
            log.debug("Creating default admin user for tenant: {}", tenant.getId());

            String adminEmail = tenant.getAdminEmail();
            String adminName = tenant.getAdminName();

            if (adminEmail == null || adminEmail.isEmpty()) {
                log.warn("Tenant {} has no admin email, skipping admin user creation", tenant.getId());
                return;
            }

            String finalAdminEmail = adminEmail;
            boolean adminExists = existingEmployees.stream()
                    .anyMatch(emp -> emp.getEmail().equalsIgnoreCase(finalAdminEmail));

            if (adminExists) {
                log.debug("Tenant {} already has an admin user: {}", tenant.getId(), adminEmail);
                return;
            }

            TenantRole adminRole = createdRoles.stream()
                    .filter(r -> "Admin".equalsIgnoreCase(r.getName()))
                    .findFirst()
                    .orElseGet(() -> roleRepository.findByTenantIdAndName(tenant.getId(), "Admin")
                            .orElse(null));

            if (adminRole == null) {
                log.warn("Admin role not found for tenant: {}, skipping admin user creation", tenant.getId());
                return;
            }

            String employeeCode = generateEmployeeCode(tenant);
            String firstName = adminName != null ? extractFirstName(adminName) : "Admin";
            String lastName = adminName != null ? extractLastName(adminName) : "User";

            EmploymentType employmentType;
            try {
                employmentType = EmploymentType.valueOf(defaultAdminEmploymentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid employment type: {}, using FULL_TIME as default", defaultAdminEmploymentType);
                employmentType = EmploymentType.FULL_TIME;
            }

            Employee adminEmployee = Employee.builder()
                    .tenant(tenant)
                    .tenantId(tenant.getId())
                    .employeeCode(employeeCode)
                    .email(adminEmail)
                    .firstName(firstName)
                    .lastName(lastName)
                    .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                    .isActive(true)
                    .status(EmployeeStatus.ACTIVE)
                    .position(defaultAdminPosition)
                    .employmentType(employmentType)
                    .hireDate(LocalDate.now())
                    .roles(Set.of(adminRole))
                    .rolesVersion(1)
                    .mustChangePassword(true)
                    .createdBy(0L)
                    .updatedBy(0L)
                    .build();

            if (defaultShift != null) {
                adminEmployee.setShift(defaultShift);
            }

            employeeRepository.save(adminEmployee);
            log.info("Created default admin user: {} for tenant: {} (Password: {})",
                    adminEmail, tenant.getId(), defaultAdminPassword);
        } catch (Exception e) {
            log.error("Error creating admin user for tenant {}: {}", tenant.getId(), e.getMessage(), e);
        }
    }

    // =====================================================
    // DEFAULT SHIFT CREATION
    // =====================================================

    @Transactional
    protected ShiftConfiguration createDefaultShiftForTenant(Long tenantId) {
        try {
            log.debug("Creating default shift for tenant: {}", tenantId);

            // Check if shift already exists
            Optional<ShiftConfiguration> existingShift = shiftConfigurationRepository
                    .findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId);

            if (existingShift.isPresent()) {
                log.debug("Tenant {} already has a default shift, skipping creation", tenantId);
                return existingShift.get();
            }

            LocalTime startTime = parseTime(defaultStartTime, LocalTime.of(9, 0));
            LocalTime endTime = parseTime(defaultEndTime, LocalTime.of(17, 0));

            ShiftConfiguration defaultShift = ShiftConfiguration.builder()
                    .tenantId(tenantId)
                    .shiftName(defaultShiftName)
                    .shiftCode(defaultShiftCode)
                    .shiftDescription(defaultShiftDescription)
                    .startTime(startTime)
                    .endTime(endTime)
                    .totalHours(calculateTotalHours(startTime, endTime))
                    .breakDurationMinutes(defaultBreakDuration)
                    .minBreakMinutes(defaultMinBreak)
                    .maxBreakMinutes(defaultMaxBreak)
                    .lateGraceMinutes(defaultLateGrace)
                    .earlyExitGraceMinutes(defaultEarlyExitGrace)
                    .checkinBufferBefore(defaultCheckinBuffer)
                    .checkoutBufferAfter(defaultCheckoutBuffer)
                    .fullDayHours(defaultFullDayHours)
                    .halfDayHours(defaultHalfDayHours)
                    .quarterDayHours(defaultQuarterDayHours)
                    .allowOvertime(defaultAllowOvertime)
                    .overtimeMultiplier(defaultOvertimeMultiplier)
                    .overtimeThresholdMinutes(defaultOvertimeThreshold)
                    .maxOvertimeHoursPerDay(defaultMaxOvertimeHours)
                    .weeklyOffs(defaultWeeklyOffs)
                    .alternateWeekOff(false)
                    .effectiveFrom(LocalDate.now())
                    .effectiveTo(null)
                    .isActive(true)
                    .isDefault(true)
                    .isDeleted(false)
                    .createdBy(0L)
                    .updatedBy(0L)
                    .build();

            ShiftConfiguration saved = shiftConfigurationRepository.save(defaultShift);
            log.info("Created default shift ({} - {}) for tenant: {}",
                    defaultStartTime, defaultEndTime, tenantId);
            return saved;
        } catch (Exception e) {
            log.error("Error creating default shift for tenant {}: {}", tenantId, e.getMessage(), e);
            return null;
        }
    }

    // =====================================================
    // UTILITY METHODS
    // =====================================================

    private String generateEmployeeCode(Tenant tenant) {
        String prefix = tenant.getTenantCode() != null ? tenant.getTenantCode().toUpperCase() : "TENANT";
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(7, 13);
        return prefix + "-ADMIN-" + timestamp;
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "Admin";
        }
        String[] parts = fullName.trim().split(" ");
        return parts[0];
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "User";
        }
        String[] parts = fullName.trim().split(" ");
        return parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : "User";
    }

    private LocalTime parseTime(String timeStr, LocalTime fallback) {
        if (timeStr == null || timeStr.isEmpty()) {
            return fallback;
        }
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            log.warn("Failed to parse time: {}, using fallback: {}", timeStr, fallback);
            return fallback;
        }
    }

    private Double calculateTotalHours(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return defaultFullDayHours;
        }
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        minutes -= (defaultBreakDuration != null ? defaultBreakDuration : 60);
        if (minutes < 0) {
            minutes = 0;
        }
        return minutes / 60.0;
    }
}