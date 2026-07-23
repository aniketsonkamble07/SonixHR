package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeCreateResponse;
import com.sonixhr.dto.employee.EmployeeUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSearchResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.employee.EmployeeDropdownDTO;
import com.sonixhr.dto.employee.BankAccountRequest;
import com.sonixhr.dto.employee.BankAccountResponse;
import com.sonixhr.dto.employee.ResignationResponse;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.leave.WeekendConfig;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.enums.employee.SalaryType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.department.DepartmentRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import com.sonixhr.repository.payroll.EmployeeSalaryProfileRepository;
import com.sonixhr.entity.payroll.EmployeeSalaryProfile;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.PermissionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import com.sonixhr.events.EmployeeUpdatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.lang.NonNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final EmailService emailService;
    private final ActivationTokenService activationTokenService;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRoleRepository roleRepository;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final com.sonixhr.service.common.AuditLogService auditLogService;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionService permissionService;

    @Value("${app.base-url}")
    private String baseUrl;

    // =====================================================
    // CREATE EMPLOYEE
    // =====================================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "tenantRoles", allEntries = true),
            @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
            @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public EmployeeCreateResponse createEmployee(@NonNull Long tenantId, EmployeeCreateRequest request) {
        log.info("Creating new employee with email: {} for tenant: {}", request.getEmail(), tenantId);

        if (employeeRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("Employee with email " + request.getEmail() + " already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        Department department = departmentRepository.findByIdAndTenantId(request.getDepartmentId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));

        String code = "EMP" + (System.currentTimeMillis() % 1000000);

        Employee employee = Employee.builder()
                .tenant(tenant)
                .employeeCode(code)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode("TempPassword@123"))
                .department(department)
                .position(request.getPosition())
                .hireDate(request.getHireDate() != null ? request.getHireDate() : LocalDate.now())
                .employmentType(request.getEmploymentType() != null ? request.getEmploymentType() : EmploymentType.FULL_TIME)
                .status(EmployeeStatus.ACTIVE)
                .isActive(true)
                .phone(request.getPhone())
                .workLocation(request.getWorkLocation())
                .build();

        Set<TenantRole> defaultRoles = new HashSet<>();
        List<TenantRole> defaults = roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
        if (defaults != null && !defaults.isEmpty()) {
            defaultRoles.add(defaults.get(0));
        } else {
            List<TenantRole> allRoles = roleRepository.findAllByTenantId(tenantId);
            if (allRoles != null && !allRoles.isEmpty()) {
                defaultRoles.add(allRoles.get(0));
            }
        }
        employee.setRoles(defaultRoles);

        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "CREATE"));

        return EmployeeCreateResponse.builder()
                .id(saved.getId())
                .employeeCode(saved.getEmployeeCode())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .departmentName(department.getName())
                .departmentCode(department.getCode())
                .position(saved.getPosition())
                .status(saved.getStatus())
                .hireDate(saved.getHireDate())
                .message("Employee created successfully")
                .build();
    }

    // =====================================================
    // UPDATE EMPLOYEE
    // =====================================================

    @Transactional
    public EmployeeResponse updateEmployee(@NonNull Long id, @NonNull Long tenantId, EmployeeUpdateRequest request) {
        // Implementation...
        return null;
    }

    // =====================================================
    // UPDATE LOGIN DETAILS - ADD THIS METHOD
    // =====================================================

    @Transactional
    public void updateLoginDetails(Long employeeId, Long tenantId) {
        log.info("Updating login details for employee: {}", employeeId);
        Employee employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId + " and tenant: " + tenantId));
        employee.setLastLoginAt(LocalDateTime.now());
        if (employee.getShift() == null) {
            shiftConfigurationRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                    .ifPresent(employee::setShift);
        }
        employeeRepository.save(employee);
        log.info("Login details updated for employee: {}", employeeId);
    }

    // =====================================================
    // ACTIVATE EMPLOYEE - ADD THIS METHOD
    // =====================================================

    @Transactional
    public Employee activateEmployee(String token, String password, String confirmPassword) {
        log.info("Activating employee with token: {}", token != null ? token.substring(0, Math.min(token.length(), 8)) + "..." : "null");

        if (!password.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        validatePasswordStrength(password);

        // Validate token
        if (activationTokenService.isTokenExpired(token)) {
            throw new BusinessException("Activation token has expired. Please request a new one.");
        }

        // Get employee ID from token
        Long employeeId = activationTokenService.getEmployeeIdFromToken(token);
        if (employeeId == null) {
            throw new BusinessException("Invalid activation token");
        }

        // Check if already activated
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (employee.isActive()) {
            throw new BusinessException("Account is already activated");
        }

        // Activate employee
        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setMustChangePassword(false);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        // Assign default shift if null
        if (employee.getShift() == null) {
            shiftConfigurationRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(employee.getTenantId())
                    .ifPresent(employee::setShift);
        }

        Employee activated = employeeRepository.save(employee);

        // Activate tenant if needed
        Tenant tenant = activated.getTenant();
        if (tenant != null && !tenant.getIsActive()) {
            tenant.activate();
            tenantRepository.save(tenant);
            log.info("Tenant activated: {}", tenant.getCompanyName());
        }

        // Invalidate the token
        activationTokenService.invalidateToken(token);

        log.info("Employee activated successfully: {}", activated.getEmail());
        return activated;
    }

    // =====================================================
    // FORGOT PASSWORD - ADD THIS METHOD
    // =====================================================

    @Transactional
    public void forgotPassword(String email) {
        log.info("Forgot password request for email: {}", email);

        // Silently return if email not found — avoids leaking whether an account exists
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(email);
        if (employeeOpt.isEmpty()) {
            log.info("Forgot-password request for unknown email (silently ignored): {}", email);
            return;
        }

        Employee employee = employeeOpt.get();
        if (!employee.isActive()) {
            // Still return silently — don't leak that the account is inactive
            log.info("Forgot-password request for non-active employee (silently ignored): {}", email);
            return;
        }

        // Generate password reset token
        String resetToken = activationTokenService.generatePasswordResetTokenForEmployee(employee.getId());
        String resetLink = baseUrl + "/api/tenant/auth/reset-password?token=" + resetToken;

        // Send reset email
        emailService.sendPasswordResetEmail(employee.getEmail(), employee.getFullName(), resetLink);
        log.info("Password reset email sent to: {}", email);
    }

    // =====================================================
    // RESET PASSWORD WITH TOKEN - ADD THIS METHOD
    // =====================================================

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword, String confirmPassword) {
        log.info("Resetting password with token");

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);

        // Validate token
        if (activationTokenService.isTokenExpired(token)) {
            throw new BusinessException("Reset token has expired. Please request a new one.");
        }

        // Get employee ID from token
        Long employeeId = activationTokenService.getEmployeeIdFromToken(token);
        if (employeeId == null) {
            throw new BusinessException("Invalid reset token");
        }

        // Find employee
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // Update password
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setMustChangePassword(false);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        employeeRepository.save(employee);

        // Invalidate the token
        activationTokenService.invalidateToken(token);

        log.info("Password reset successfully for employee: {}", employee.getEmail());
    }

    // =====================================================
    // RESEND ACTIVATION EMAIL
    // =====================================================

    @Transactional
    public void resendActivationEmail(String email) {
        log.info("Resending activation email to: {}", email);

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));

        if (employee.isActive()) {
            throw new BusinessException("Employee is already activated");
        }

        // Generate new activation token
        String activationToken = activationTokenService.generateTokenForEmployee(employee.getId());
        String activationLink = baseUrl + "/api/tenant/auth/activate?token=" + activationToken;

        // Send activation email
        emailService.sendActivationEmail(employee.getEmail(), employee.getFullName(), activationLink);

        log.info("Activation email resent to: {}", email);
    }

    // =====================================================
    // CHANGE PASSWORD
    // =====================================================

    @Transactional
    public void changePassword(@NonNull Long employeeId, String oldPassword, String newPassword,
                               String confirmPassword) {
        log.info("Changing password for employee: {}", employeeId);

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("New passwords do not match");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (!passwordEncoder.matches(oldPassword, employee.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        validatePasswordStrength(newPassword);

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setMustChangePassword(false);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        employeeRepository.save(employee);

        log.info("Password changed successfully for employee: {}", employeeId);
    }

    // =====================================================
    // RESET PASSWORD BY ADMIN
    // =====================================================

    @Transactional
    public void resetPasswordByAdmin(@NonNull Long employeeId, String newPassword) {
        log.info("Resetting password by admin for employee: {}", employeeId);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        validatePasswordStrength(newPassword);

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setMustChangePassword(true);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        employeeRepository.save(employee);

        log.info("Password reset by admin for employee: {}", employeeId);
    }

    // =====================================================
    // GET EMPLOYEE BY ID
    // =====================================================

    public EmployeeResponse getEmployeeById(@NonNull Long id, @NonNull Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY CODE
    // =====================================================

    public EmployeeResponse getEmployeeByCode(@NonNull Long tenantId, String employeeCode) {
        Employee employee = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY EMAIL
    // =====================================================

    public EmployeeResponse getEmployeeByEmail(@NonNull Long tenantId, String email) {
        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET ALL EMPLOYEES (PAGINATED)
    // =====================================================

    public Page<EmployeeSummaryResponse> getAllEmployees(@NonNull Long tenantId, Pageable pageable) {
        return employeeRepository.findByTenant_Id(tenantId, pageable)
                .map(this::convertToSummaryResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY STATUS
    // =====================================================

    public Page<EmployeeSummaryResponse> getEmployeesByStatus(@NonNull Long tenantId, EmployeeStatus status,
                                                              Pageable pageable) {
        return employeeRepository.findByTenant_IdAndStatus(tenantId, status, pageable)
                .map(this::convertToSummaryResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY DEPARTMENT NAME
    // =====================================================

    @Transactional(readOnly = true)
    public List<EmployeeSummaryResponse> getEmployeesByDepartmentName(Long tenantId, String departmentName) {
        log.debug("Fetching employees in department: {} for tenant: {}", departmentName, tenantId);
        return employeeRepository.findByTenantIdAndDepartmentName(tenantId, departmentName)
                .stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET TEAM MEMBERS (Paginated)
    // =====================================================

    public Page<EmployeeSummaryResponse> getTeamMembersPaginated(@NonNull Long tenantId, @NonNull Long managerId,
                                                                 Pageable pageable) {
        log.info("Getting team members for manager: {} with pagination", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        return employeeRepository.findByManagerIdAndTenantId(managerId, tenantId, pageable)
                .map(this::convertToSummaryResponse);
    }

    // =====================================================
    // GET TEAM MEMBERS (List)
    // =====================================================

    public List<EmployeeSummaryResponse> getTeamMembers(@NonNull Long managerId, @NonNull Long tenantId) {
        log.info("Getting team members for manager: {}", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        return employeeRepository.findByManagerIdAndTenantId(managerId, tenantId)
                .stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET ALL SUBORDINATES
    // =====================================================

    public List<EmployeeSummaryResponse> getAllSubordinates(@NonNull Long managerId, @NonNull Long tenantId) {
        log.info("Getting all subordinates for manager: {}", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        List<Employee> allSubordinates = new ArrayList<>();
        collectAllSubordinates(managerId, tenantId, allSubordinates);
        return allSubordinates.stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    private void collectAllSubordinates(@NonNull Long managerId, @NonNull Long tenantId, List<Employee> result) {
        List<Employee> directReports = employeeRepository.findByManagerIdAndTenantId(managerId, tenantId);
        result.addAll(directReports);
        for (Employee report : directReports) {
            Long reportId = report.getId();
            if (reportId != null) {
                collectAllSubordinates(reportId, tenantId, result);
            }
        }
    }

    // =====================================================
    // GET MANAGER CHAIN
    // =====================================================

    public List<EmployeeSummaryResponse> getManagerChain(@NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Getting manager chain for employee: {}", employeeId);
        Employee employee = findEmployeeByIdAndTenant(employeeId, tenantId);
        List<Employee> chain = new ArrayList<>();
        Employee current = employee.getManager();
        while (current != null) {
            chain.add(current);
            current = current.getManager();
        }
        return chain.stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET EMPLOYEES WITH NO MANAGER
    // =====================================================

    public List<EmployeeSummaryResponse> getEmployeesWithNoManager(@NonNull Long tenantId) {
        log.info("Getting employees with no manager for tenant: {}", tenantId);
        return employeeRepository.findEmployeesWithNoManager(tenantId)
                .stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // CHECK IF EMPLOYEE IS MANAGER
    // =====================================================

    public boolean isManager(@NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Checking if employee is manager: {}", employeeId);
        findEmployeeByIdAndTenant(employeeId, tenantId);
        long directReportsCount = employeeRepository.countByManagerIdAndTenantId(employeeId, tenantId);
        return directReportsCount > 0;
    }

    // =====================================================
    // SEARCH EMPLOYEES FOR ASSIGNMENT
    // =====================================================

    @Transactional(readOnly = true)
    public Page<EmployeeSearchResponse> searchEmployeesForAssignment(Long tenantId, String query, Pageable pageable) {
        log.info("Searching employees for assignment with query: {}", query);
        return employeeRepository.searchEmployeesForAssignment(tenantId, query, pageable)
                .map(this::convertToSearchResponse);
    }

    // =====================================================
    // UPDATE EMPLOYEE STATUS
    // =====================================================

    @Transactional
    public void updateEmployeeStatus(@NonNull Long id, @NonNull Long tenantId, EmployeeStatus newStatus,
                                     String reason) {
        if (!permissionService.hasPermission("EMPLOYEE_EDIT") && !permissionService.hasPermission("ROLE_ASSIGN") && !permissionService.isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied: EMPLOYEE_EDIT or ROLE_ASSIGN permission required");
        }

        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        EmployeeStatus oldStatus = employee.getStatus();

        if ((newStatus == EmployeeStatus.ACTIVE || newStatus == EmployeeStatus.PROBATION) && !employee.isActive()) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            long activeEmployeeCount = employeeRepository.countActiveByTenantId(tenantId);
            if (tenant.getMaxEmployees() != null && tenant.getMaxEmployees() > 0
                    && activeEmployeeCount >= tenant.getMaxEmployees()) {
                throw new BusinessException("Active employee limit of " + tenant.getMaxEmployees()
                        + " reached for your subscription. Please upgrade your plan.");
            }
        }

        switch (newStatus) {
            case ACTIVE:
                employee.activate();
                break;
            case PROBATION:
                employee.putOnProbation();
                break;
            case RESIGNED:
                if (reason == null || reason.isEmpty()) {
                    throw new BusinessException("Resignation reason is required");
                }
                employee.resign(LocalDate.now(), LocalDate.now().plusDays(30));
                break;
            case TERMINATED:
                employee.terminate();
                break;
            default:
                employee.setStatus(newStatus);
        }
        Employee saved = employeeRepository.save(employee);
        log.info("Employee status updated from {} to {}", oldStatus, newStatus);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "STATUS_CHANGE"));
    }

    // =====================================================
    // CONFIRM EMPLOYEE
    // =====================================================

    @Transactional
    public void confirmEmployee(@NonNull Long id, @NonNull Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        if (!employee.isOnProbation()) {
            throw new BusinessException("Employee is not on probation");
        }
        employee.confirmEmployee();
        employeeRepository.save(employee);
        log.info("Employee confirmed successfully: {}", id);
    }

    // =====================================================
    // SEARCH EMPLOYEES
    // =====================================================

    public Page<EmployeeSummaryResponse> searchEmployees(@NonNull Long tenantId, String searchTerm, Pageable pageable) {
        return employeeRepository.searchEmployees(tenantId, searchTerm, pageable)
                .map(this::convertToSummaryResponse);
    }

    // =====================================================
    // DELETE EMPLOYEE (SOFT DELETE)
    // =====================================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "tenantRoles", allEntries = true),
            @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
            @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public void deleteEmployee(@NonNull Long id, @NonNull Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        List<Employee> subordinates = employeeRepository.findByManagerIdAndTenantId(id, tenantId);
        if (!subordinates.isEmpty()) {
            throw new BusinessException("Cannot delete employee with " + subordinates.size() + " subordinates");
        }
        String oldStatus = employee.getStatus() != null ? employee.getStatus().name() : "null";
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setLastWorkingDate(LocalDate.now());
        employee.setActive(false);
        Employee saved = employeeRepository.save(employee);

        Long performedBy = com.sonixhr.security.TenantContext.getCurrentUserId();
        auditLogService.log(
                saved.getTenant(),
                "EMPLOYEE_DELETED",
                "status",
                oldStatus,
                EmployeeStatus.TERMINATED.name(),
                performedBy,
                "{\"employeeId\":" + id + "}");

        eventPublisher.publishEvent(EmployeeUpdatedEvent.deactivated(saved.getEmail(), saved.getId()));
        log.info("Employee soft deleted successfully: {}", id);
    }

    // =====================================================
    // GET DEPARTMENT STATISTICS
    // =====================================================

    public List<DepartmentStat> getDepartmentStatistics(Long tenantId) {
        List<Object[]> results = employeeRepository.countEmployeesByDepartment(tenantId);
        return results.stream()
                .map(row -> DepartmentStat.builder()
                        .department((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET UPCOMING BIRTHDAYS
    // =====================================================

    public List<EmployeeSummaryResponse> getUpcomingBirthdays(Long tenantId, int days) {
        log.debug("Getting upcoming birthdays for tenant: {} within {} days", tenantId, days);

        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        return employeeRepository.findEmployeesWithUpcomingBirthdays(tenantId, today, futureDate)
                .stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET UPCOMING ANNIVERSARIES
    // =====================================================

    public List<EmployeeSummaryResponse> getUpcomingAnniversaries(Long tenantId, int days) {
        log.debug("Getting upcoming anniversaries for tenant: {} within {} days", tenantId, days);

        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        return employeeRepository.findEmployeesWithUpcomingAnniversaries(tenantId, today, futureDate)
                .stream()
                .map(this::convertToSummaryResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // ACTIVE EMPLOYEES FOR DROPDOWN
    // =====================================================

    public List<EmployeeDropdownDTO> getActiveEmployeesForDropdown(@NonNull Long tenantId) {
        log.info("Getting active employees for dropdown for tenant: {}", tenantId);
        return employeeRepository.findActiveEmployeesForDropdown(tenantId);
    }

    // =====================================================
    // ASSIGN MANAGER BY CODE
    // =====================================================

    @Transactional
    public EmployeeResponse assignManagerByCode(String employeeCode, String managerCode, @NonNull Long tenantId,
                                                String reason) {
        log.info("Assigning manager by code - Employee: {} to Manager: {}", employeeCode, managerCode);

        Employee employee = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));

        Employee manager = null;
        if (managerCode != null && !managerCode.isEmpty()) {
            manager = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, managerCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with code: " + managerCode));
        }

        validateManagerAssignment(employee, manager, tenantId);

        employee.setManager(manager);
        employee.setUpdatedBy(getCurrentEmployeeId());
        Employee saved = employeeRepository.save(employee);

        log.info("Manager assigned successfully for employee: {}", employeeCode);
        return convertToResponse(saved);
    }

    // =====================================================
    // REMOVE MANAGER
    // =====================================================

    @Transactional
    public void removeManager(String employeeCode, @NonNull Long tenantId, String reason) {
        log.info("Removing manager for employee: {} with reason: {}", employeeCode, reason);

        Employee employee = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));

        employee.setManager(null);
        employee.setUpdatedBy(getCurrentEmployeeId());
        employeeRepository.save(employee);

        log.info("Manager removed successfully for employee: {}", employeeCode);
    }

    // =====================================================
    // PROCESS OFFBOARDED EMPLOYEES
    // =====================================================

    @Transactional
    public void processOffboardedEmployees(Long tenantId) {
        log.info("Processing offboarded employees for tenant: {}", tenantId);
        LocalDate today = LocalDate.now();
        List<Employee> offboardedEmployees = employeeRepository.findActiveEmployeesWithExpiredLastWorkingDate(today);
        log.info("Found {} employees with expired last working date to deactivate", offboardedEmployees.size());

        for (Employee employee : offboardedEmployees) {
            try {
                employee.setActive(false);
                Employee saved = employeeRepository.save(employee);
                eventPublisher.publishEvent(EmployeeUpdatedEvent.deactivated(saved.getEmail(), saved.getId()));
                log.info("Manually deactivated offboarded employee ID: {}, Email: {} (Last working date: {})",
                        saved.getId(), saved.getEmail(), saved.getLastWorkingDate());
            } catch (Exception e) {
                log.error("Failed to deactivate offboarded employee ID: {}: {}", employee.getId(), e.getMessage());
            }
        }
    }

    // =====================================================
    // ORGANIZATION CHART
    // =====================================================

    public List<EmployeeSummaryResponse> getOrganizationChart(Long tenantId) {
        log.debug("Getting organization chart for tenant: {}", tenantId);

        List<Employee> allEmployees = employeeRepository.findByTenant_Id(tenantId);
        Map<Long, List<Employee>> managerToSubordinates = new HashMap<>();

        for (Employee emp : allEmployees) {
            Long managerId = emp.getManager() != null ? emp.getManager().getId() : null;
            managerToSubordinates.computeIfAbsent(managerId, k -> new ArrayList<>()).add(emp);
        }

        return managerToSubordinates.getOrDefault(null, Collections.emptyList())
                .stream()
                .map(emp -> buildHierarchyResponse(emp, managerToSubordinates))
                .collect(Collectors.toList());
    }

    // =====================================================
    // RESIGNATION METHODS
    // =====================================================

    @Transactional
    public void submitResignation(Long id, Long tenantId, String reason, LocalDate proposedLWD) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        employee.submitResignation(reason, proposedLWD);
        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "RESIGNATION_SUBMITTED"));
    }

    @Transactional
    public void acceptResignation(Long id, Long tenantId, LocalDate approvedLWD) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        employee.acceptResignation(approvedLWD);
        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "RESIGNATION_ACCEPTED"));
    }

    @Transactional
    public void rejectResignation(Long id, Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        employee.rejectOrWithdrawResignation(com.sonixhr.enums.employee.ResignationStatus.REJECTED);
        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "RESIGNATION_REJECTED"));
    }

    @Transactional
    public void withdrawResignation(Long id, Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        employee.rejectOrWithdrawResignation(com.sonixhr.enums.employee.ResignationStatus.WITHDRAWN);
        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(new EmployeeUpdatedEvent(saved.getEmail(), saved.getId(), "RESIGNATION_WITHDRAWN"));
    }

    public ResignationResponse getResignationDetails(Long id, Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        return convertToResignationResponse(employee);
    }

    @Transactional
    public void markEmployeeAbsconded(Long id, Long tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        employee.markAbsconded();
        Employee saved = employeeRepository.save(employee);
        eventPublisher.publishEvent(EmployeeUpdatedEvent.deactivated(saved.getEmail(), saved.getId()));
    }

    // =====================================================
    // DEACTIVATE OFFBOARDED EMPLOYEES SCHEDULED
    // =====================================================

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 5 1 * * ?")
    @Transactional
    public void deactivateOffboardedEmployees() {
        log.info("Running daily deactivation of offboarded employees");
        LocalDate today = LocalDate.now();
        List<Employee> offboardedEmployees = employeeRepository.findActiveEmployeesWithExpiredLastWorkingDate(today);
        log.info("Found {} employees with expired last working date to deactivate", offboardedEmployees.size());

        for (Employee employee : offboardedEmployees) {
            try {
                employee.setActive(false);
                employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.RESIGNED);
                Employee saved = employeeRepository.save(employee);
                eventPublisher.publishEvent(EmployeeUpdatedEvent.deactivated(saved.getEmail(), saved.getId()));
                log.info("Deactivated offboarded employee ID: {}, Email: {} (Last working date: {})",
                        saved.getId(), saved.getEmail(), saved.getLastWorkingDate());
            } catch (Exception e) {
                log.error("Failed to deactivate offboarded employee ID: {}: {}", employee.getId(), e.getMessage());
            }
        }
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private Employee buildEmployeeFromRequest(Tenant tenant, EmployeeCreateRequest request, String employeeCode) {
        // Implementation...
        return null;
    }

    private void validateStateForCountry(IndianState state, String country) {
        if ("IN".equalsIgnoreCase(country) && state == null) {
            throw new com.sonixhr.exceptions.ValidationException("state", "State is required for employees in India");
        }
    }

    private Map<String, Object> convertBankDetailsToMap(BankAccountRequest bankDetails) {
        if (bankDetails == null) {
            return new HashMap<>();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("bankName", bankDetails.getBankName());
        map.put("accountHolderName", bankDetails.getAccountHolderName());
        map.put("accountNumber", bankDetails.getAccountNumber());
        map.put("ifscCode", bankDetails.getIfscCode());
        map.put("branchName", bankDetails.getBranchName());
        map.put("accountType", bankDetails.getAccountType());
        map.put("isPrimary", bankDetails.isPrimary());
        return map;
    }

    private BankAccountResponse convertBankDetailsToResponse(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String accountNo = (String) map.get("accountNumber");
        String masked = accountNo != null && accountNo.length() > 4
                ? "XXXX" + accountNo.substring(accountNo.length() - 4)
                : accountNo;

        return BankAccountResponse.builder()
                .bankName((String) map.get("bankName"))
                .accountHolderName((String) map.get("accountHolderName"))
                .maskedAccountNumber(masked)
                .ifscCode((String) map.get("ifscCode"))
                .branchName((String) map.get("branchName"))
                .accountType((String) map.get("accountType"))
                .isPrimary(map.get("isPrimary") instanceof Boolean ? (Boolean) map.get("isPrimary") : true)
                .isActive(map.get("isActive") instanceof Boolean ? (Boolean) map.get("isActive") : true)
                .build();
    }

    private Long getCurrentEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Employee employee) {
                return employee.getId();
            }
        }
        return null;
    }

    private Employee findEmployeeByIdAndTenant(@NonNull Long id, @NonNull Long tenantId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        if (!employee.getTenantId().equals(tenantId) || employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        return employee;
    }

    private void validateManagerAssignment(Employee employee, Employee manager, Long tenantId) {
        if (manager == null) {
            log.info("Removing manager for employee: {}", employee.getEmployeeCode());
            return;
        }

        if (employee.getId() != null && employee.getId().equals(manager.getId())) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Employee cannot be their own manager");
        }

        if (!manager.getTenantId().equals(tenantId)) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Manager must be from the same tenant");
        }

        if (!manager.isActive()) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Manager must be an active employee");
        }

        if (manager.isOnProbation()) {
            throw new com.sonixhr.exceptions.ValidationException("managerId",
                    "Employees on probation cannot be managers");
        }

        if (isCircularReference(employee, manager)) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Circular manager reference detected");
        }
    }

    private boolean isCircularReference(Employee employee, Employee potentialManager) {
        Set<Long> visited = new HashSet<>();
        Employee current = potentialManager;

        while (current != null) {
            if (employee.getId() != null && current.getId().equals(employee.getId())) {
                return true;
            }
            if (visited.contains(current.getId())) {
                return true;
            }
            visited.add(current.getId());
            current = current.getManager();
        }
        return false;
    }

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
            throw new BusinessException("Password must contain at least one special character");
        }
    }

    // =====================================================
    // CONVERT TO RESPONSE METHODS
    // =====================================================

    public EmployeeCreateResponse convertToCreateResponse(Employee employee) {
        return EmployeeCreateResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .departmentCode(employee.getDepartment() != null ? employee.getDepartment().getCode() : null)
                .position(employee.getPosition())
                .status(employee.getStatus())
                .hireDate(employee.getHireDate())
                .message("Employee created successfully")
                .build();
    }

    public ResignationResponse convertToResignationResponse(Employee employee) {
        if (employee == null) {
            return null;
        }
        return ResignationResponse.builder()
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .employeeName(employee.getFullName())
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .position(employee.getPosition())
                .status(employee.getStatus())
                .resignationStatus(employee.getResignationStatus())
                .resignationReason(employee.getResignationReason())
                .resignationDate(employee.getResignationDate())
                .proposedLastWorkingDate(employee.getProposedLastWorkingDate())
                .approvedLastWorkingDate(employee.getApprovedLastWorkingDate())
                .lastWorkingDate(employee.getLastWorkingDate())
                .isResignationAccepted(employee.isResignationAccepted())
                .build();
    }

    public EmployeeResponse convertToResponse(Employee employee) {
        EmployeeResponse.EmployeeResponseBuilder builder = EmployeeResponse.builder()
                .id(employee.getId())
                .tenantId(employee.getTenantId())
                .employeeCode(employee.getEmployeeCode())
                .userId(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFullName())
                .initials(employee.getInitials())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender())
                .maritalStatus(employee.getMaritalStatus())
                .bloodGroup(employee.getBloodGroup())
                .nationality(employee.getNationality())
                .personalEmail(employee.getPersonalEmail())
                .department(employee.getDepartment() != null ? EmployeeResponse.DepartmentInfo.builder()
                        .id(employee.getDepartment().getId())
                        .name(employee.getDepartment().getName())
                        .code(employee.getDepartment().getCode())
                        .build() : null)
                .position(employee.getPosition())
                .manager(employee.getManager() != null ? EmployeeResponse.ManagerInfo.builder()
                        .id(employee.getManager().getId())
                        .fullName(employee.getManager().getFullName())
                        .email(employee.getManager().getEmail())
                        .position(employee.getManager().getPosition())
                        .department(employee.getManager().getDepartment() != null
                                ? employee.getManager().getDepartment().getName()
                                : null)
                        .employeeCode(employee.getManager().getEmployeeCode())
                        .build() : null)
                .shift(employee.getShift() != null ? EmployeeResponse.ShiftInfo.builder()
                        .id(employee.getShift().getId())
                        .shiftName(employee.getShift().getShiftName())
                        .shiftCode(employee.getShift().getShiftCode())
                        .startTime(employee.getShift().getStartTime() != null
                                ? employee.getShift().getStartTime().toString()
                                : null)
                        .endTime(employee.getShift().getEndTime() != null ? employee.getShift().getEndTime().toString()
                                : null)
                        .build() : null)
                .employmentType(employee.getEmploymentType())
                .workLocation(employee.getWorkLocation())
                .workState(employee.getWorkState())
                .workStateText(employee.getWorkStateText())
                .workCountry(employee.getWorkCountry())
                .hireDate(employee.getHireDate())
                .confirmationDate(employee.getConfirmationDate())
                .resignationDate(employee.getResignationDate())
                .lastWorkingDate(employee.getLastWorkingDate())
                .tenureInMonths(employee.getTenureInMonths())
                .status(employee.getStatus())
                .isActive(employee.isActive())
                .address(employee.getAddress())
                .city(employee.getCity())
                .state(employee.getState())
                .stateText(employee.getStateText())
                .country(employee.getCountry())
                .postalCode(employee.getPostalCode())
                .emergencyContactName(employee.getEmergencyContactName())
                .emergencyContactPhone(employee.getEmergencyContactPhone())
                .emergencyContactRelation(employee.getEmergencyContactRelation())
                .emergencyContactEmail(employee.getEmergencyContactEmail())
                .secondaryEmergencyName(employee.getSecondaryEmergencyName())
                .secondaryEmergencyPhone(employee.getSecondaryEmergencyPhone())
                .profilePictureUrl(employee.getProfilePictureUrl())
                .bankDetails(convertBankDetailsToResponse(employee.getBankDetails()))
                .documents(employee.getDocuments())
                .certifications(employee.getCertifications())
                .linkedinUrl(employee.getLinkedinUrl())
                .githubUrl(employee.getGithubUrl())
                .twitterUrl(employee.getTwitterUrl())
                .weekendConfig(employee.getWeekendConfig())
                .customWeekendDays(employee.getCustomWeekendDays())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .createdBy(employee.getCreatedBy())
                .updatedBy(employee.getUpdatedBy());

        if (employeeSalaryProfileRepository != null) {
            List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepository
                    .findActiveByEmployeeId(employee.getId());
            if (profiles != null && !profiles.isEmpty()) {
                EmployeeSalaryProfile activeProfile = profiles.get(0);
                builder.monthlyCtc(activeProfile.getMonthlyCtc())
                        .currency(activeProfile.getCurrency())
                        .taxRegime(activeProfile.getTaxRegime());
            }
        }

        return builder.build();
    }

    public EmployeeSummaryResponse convertToSummaryResponse(Employee employee) {
        if (employee == null) {
            return null;
        }
        return EmployeeSummaryResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .position(employee.getPosition())
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .status(employee.getStatus())
                .isActive(employee.isActive())
                .profilePictureUrl(employee.getProfilePictureUrl())
                .hireDate(employee.getHireDate())
                .managerName(employee.getManager() != null ? employee.getManager().getFullName() : null)
                .build();
    }

    private EmployeeSearchResponse convertToSearchResponse(Employee employee) {
        return EmployeeSearchResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .position(employee.getPosition())
                .department(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .status(employee.getStatus())
                .currentManager(employee.getManager() != null ? employee.getManager().getFullName() : null)
                .profilePictureUrl(employee.getProfilePictureUrl())
                .build();
    }

    private EmployeeSummaryResponse buildHierarchyResponse(Employee employee,
                                                           Map<Long, List<Employee>> managerToSubordinates) {
        EmployeeSummaryResponse response = convertToSummaryResponse(employee);
        List<Employee> subordinates = managerToSubordinates.getOrDefault(employee.getId(), Collections.emptyList());
        response.setDirectReports(subordinates.stream()
                .map(sub -> buildHierarchyResponse(sub, managerToSubordinates))
                .collect(Collectors.toList()));
        return response;
    }
}