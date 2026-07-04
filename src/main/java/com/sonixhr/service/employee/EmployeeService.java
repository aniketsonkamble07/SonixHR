package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSearchResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.employee.EmployeeDropdownDTO;
import com.sonixhr.enums.IndianState;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import org.springframework.lang.NonNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
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
    public EmployeeResponse createEmployee(@NonNull Long tenantId, EmployeeCreateRequest request) {
        log.info("Creating employee for tenant: {}", tenantId);
        log.debug("Request roleIds: {}", request.getRoleIds());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        // Check if employee with this email already exists
        if (employeeRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
            throw new com.sonixhr.exceptions.ValidationException("email", "Employee with email " + request.getEmail() + " already exists");
        }

        // Validate roles BEFORE building employee
        Set<Long> roleIds = request.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            log.error("No roles provided for employee creation");
            throw new com.sonixhr.exceptions.ValidationException("roleIds", "At least one role is required for employee");
        }

        // Fetch roles
        List<TenantRole> roles = roleRepository.findAllById(roleIds);
        if (roles.isEmpty()) {
            throw new com.sonixhr.exceptions.ValidationException("roleIds", "No valid roles found for the provided role IDs");
        }
        log.info("Found {} roles for employee", roles.size());

        // Generate employee code
        String employeeCode = employeeCodeGenerator.generateEmployeeCode(tenant);

        // Build employee with roles
        Employee employee = buildEmployeeFromRequest(tenant, request, employeeCode);

        // Add roles BEFORE save
        employee.getRoles().addAll(roles);

        // Set manager if provided
        Long managerId = request.getManagerId();
        String managerCode = request.getManagerCode();
        if (managerCode != null && !managerCode.trim().isEmpty()) {
            Employee manager = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, managerCode.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with code: " + managerCode));
            employee.setManager(manager);
        } else if (managerId != null) {
            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
            if (!manager.getTenantId().equals(tenantId)) {
                throw new BusinessException("Manager must be from the same tenant");
            }
            employee.setManager(manager);
        }

        // Set default values (active immediately for testing)
        employee.setPasswordHash(passwordEncoder.encode("Admin@123"));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setActive(true);

        // Fetch default shift for tenant and assign
        shiftConfigurationRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                .ifPresent(employee::setShift);

        // Save employee (validation will pass because roles are set)
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with code: {} and {} roles", employeeCode, roles.size());

        // Generate activation token & send activation email (disabled for development testing)
        // String activationToken = activationTokenService.generateTokenForEmployee(savedEmployee.getId());
        // String activationLink = baseUrl + "/api/tenant/auth/activate?token=" + activationToken;
        // emailService.sendActivationEmail(savedEmployee.getEmail(), savedEmployee.getFullName(), activationLink);
        // log.info("Activation email sent to new employee: {}", savedEmployee.getEmail());

        if (request.getSalary() != null) {
            java.math.BigDecimal monthlyCtc;
            if (request.getSalaryType() == SalaryType.YEARLY) {
                monthlyCtc = request.getSalary().divide(java.math.BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
            } else {
                monthlyCtc = request.getSalary();
            }

            EmployeeSalaryProfile salaryProfile = EmployeeSalaryProfile.builder()
                    .tenant(savedEmployee.getTenant())
                    .employee(savedEmployee)
                    .monthlyCtc(monthlyCtc)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                    .taxRegime(request.getTaxRegime() != null ? request.getTaxRegime() : "NEW_REGIME")
                    .version(1)
                    .effectiveFrom(savedEmployee.getHireDate() != null ? savedEmployee.getHireDate() : LocalDate.now())
                    .isActive(true)
                    .createdBy(getCurrentEmployeeId())
                    .build();
            employeeSalaryProfileRepository.save(salaryProfile);
        }

        return convertToResponse(savedEmployee);
    }

    // =====================================================
    // UPDATE EMPLOYEE
    // =====================================================
    @Transactional
    public EmployeeResponse updateEmployee(@NonNull Long id, @NonNull Long tenantId, EmployeeUpdateRequest request) {
        log.info("Updating employee with id: {} for tenant: {}", id, tenantId);

        Employee employee = findEmployeeByIdAndTenant(id, tenantId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasEditAuthority = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_EDIT"));

        if (!hasEditAuthority) {
            Map<String, String> errors = new HashMap<>();
            String msg = "Only HR or Super Admin can update this restricted field";

            if (request.getSalary() != null) {
                List<EmployeeSalaryProfile> activeProfiles = employeeSalaryProfileRepository.findActiveByEmployeeId(id);
                java.math.BigDecimal currentSalary = (activeProfiles != null && !activeProfiles.isEmpty()) ? activeProfiles.get(0).getMonthlyCtc() : null;
                java.math.BigDecimal targetSalary = request.getSalary();
                if (request.getSalaryType() == SalaryType.YEARLY) {
                    targetSalary = targetSalary.divide(java.math.BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
                }
                if (currentSalary == null || currentSalary.compareTo(targetSalary) != 0) {
                    errors.put("salary", msg);
                }
            }
            if (request.getDepartmentId() != null && (employee.getDepartment() == null || !employee.getDepartment().getId().equals(request.getDepartmentId()))) {
                errors.put("departmentId", msg);
            }
            if (request.getPosition() != null && !request.getPosition().equals(employee.getPosition())) {
                errors.put("position", msg);
            }
            if (request.getWorkLocation() != null && !request.getWorkLocation().equals(employee.getWorkLocation())) {
                errors.put("workLocation", msg);
            }
            if (request.getEmploymentType() != null && request.getEmploymentType() != employee.getEmploymentType()) {
                errors.put("employmentType", msg);
            }
            if (request.getHireDate() != null && !request.getHireDate().equals(employee.getHireDate())) {
                errors.put("hireDate", msg);
            }
            if (request.getManagerId() != null && (employee.getManager() == null || !employee.getManager().getId().equals(request.getManagerId()))) {
                errors.put("managerId", msg);
            }
            if (request.getManagerCode() != null && (employee.getManager() == null || !employee.getManager().getEmployeeCode().equals(request.getManagerCode()))) {
                errors.put("managerCode", msg);
            }
            if (request.getShiftId() != null && (employee.getShift() == null || !employee.getShift().getId().equals(request.getShiftId()))) {
                errors.put("shiftId", msg);
            }
            if (request.getBankDetails() != null && (employee.getBankDetails() == null || !employee.getBankDetails().equals(request.getBankDetails()))) {
                errors.put("bankDetails", msg);
            }

            if (!errors.isEmpty()) {
                throw new com.sonixhr.exceptions.ValidationException(errors);
            }
        }

        // Update personal information
        if (request.getFirstName() != null)
            employee.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            employee.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null)
            employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null)
            employee.setGender(request.getGender());
        if (request.getMaritalStatus() != null)
            employee.setMaritalStatus(request.getMaritalStatus());
        if (request.getBloodGroup() != null)
            employee.setBloodGroup(request.getBloodGroup());
        if (request.getNationality() != null)
            employee.setNationality(request.getNationality());
        if (request.getPersonalEmail() != null)
            employee.setPersonalEmail(request.getPersonalEmail());
        if (request.getBankDetails() != null)
            employee.setBankDetails(request.getBankDetails());
        if (request.getShiftId() != null) {
            com.sonixhr.entity.attendance.ShiftConfiguration shift = shiftConfigurationRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
            employee.setShift(shift);
        }
        if (request.getEmail() != null && !request.getEmail().equals(employee.getEmail())) {
            if (employeeRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
                throw new com.sonixhr.exceptions.ValidationException("email", "Employee with email " + request.getEmail() + " already exists");
            }
            employee.setEmail(request.getEmail());
        }
        if (request.getPhone() != null)
            employee.setPhone(request.getPhone());

        // Update professional information
        Long departmentId = request.getDepartmentId();
        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            employee.setDepartment(department);
        }
        if (request.getPosition() != null)
            employee.setPosition(request.getPosition());

        if (request.getEmploymentType() != null) {
            employee.setEmploymentType(request.getEmploymentType());
        }
        if (request.getWorkLocation() != null) {
            employee.setWorkLocation(request.getWorkLocation());
        }
        boolean addressUpdated = false;
        if (request.getAddress() != null) {
            employee.setAddress(request.getAddress());
            addressUpdated = true;
        }
        if (request.getCity() != null)
            employee.setCity(request.getCity());
        if (request.getState() != null)
            employee.setState(request.getState());
        if (request.getStateText() != null)
            employee.setStateText(request.getStateText());
        if (request.getCountry() != null)
            employee.setCountry(com.sonixhr.util.CountryUtils.normalizeAndValidateCountry(request.getCountry()));
        if (request.getPostalCode() != null)
            employee.setPostalCode(request.getPostalCode());

        if (request.getHireDate() != null)
            employee.setHireDate(request.getHireDate());

        // Update manager if changed
        Long managerId = request.getManagerId();
        String managerCode = request.getManagerCode();
        if (managerCode != null && !managerCode.trim().isEmpty()) {
            Employee newManager = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, managerCode.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with code: " + managerCode));
            validateManagerAssignment(employee, newManager, tenantId);
            employee.setManager(newManager);
        } else if (managerId != null && (employee.getManager() == null ||
                !employee.getManager().getId().equals(managerId))) {
            Employee newManager = findEmployeeByIdAndTenant(managerId, tenantId);
            validateManagerAssignment(employee, newManager, tenantId);
            employee.setManager(newManager);
        }

        if (request.getWeekendConfig() != null)
            employee.setWeekendConfig(request.getWeekendConfig());
        if (request.getCustomWeekendDays() != null)
            employee.setCustomWeekendDays(request.getCustomWeekendDays());

        employee.setUpdatedBy(getCurrentEmployeeId());
        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", id);

        if (request.getSalary() != null) {
            java.math.BigDecimal monthlyCtc;
            if (request.getSalaryType() == SalaryType.YEARLY) {
                monthlyCtc = request.getSalary().divide(java.math.BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
            } else {
                monthlyCtc = request.getSalary();
            }

            String currency = request.getCurrency() != null ? request.getCurrency() : "INR";
            String taxRegime = request.getTaxRegime() != null ? request.getTaxRegime() : "NEW_REGIME";

            List<EmployeeSalaryProfile> activeProfiles = employeeSalaryProfileRepository.findActiveByEmployeeId(id);
            if (activeProfiles != null && !activeProfiles.isEmpty()) {
                EmployeeSalaryProfile activeProfile = activeProfiles.get(0);
                if (activeProfile.getMonthlyCtc().compareTo(monthlyCtc) != 0 ||
                        !activeProfile.getCurrency().equalsIgnoreCase(currency) ||
                        !activeProfile.getTaxRegime().equalsIgnoreCase(taxRegime)) {
                    
                    activeProfile.setActive(false);
                    activeProfile.setEffectiveTo(LocalDate.now().minusDays(1));
                    employeeSalaryProfileRepository.save(activeProfile);

                    EmployeeSalaryProfile newProfile = EmployeeSalaryProfile.builder()
                            .tenant(updatedEmployee.getTenant())
                            .employee(updatedEmployee)
                            .monthlyCtc(monthlyCtc)
                            .currency(currency)
                            .taxRegime(taxRegime)
                            .version(activeProfile.getVersion() + 1)
                            .effectiveFrom(LocalDate.now())
                            .isActive(true)
                            .createdBy(getCurrentEmployeeId())
                            .build();
                    employeeSalaryProfileRepository.save(newProfile);
                }
            } else {
                EmployeeSalaryProfile newProfile = EmployeeSalaryProfile.builder()
                        .tenant(updatedEmployee.getTenant())
                        .employee(updatedEmployee)
                        .monthlyCtc(monthlyCtc)
                        .currency(currency)
                        .taxRegime(taxRegime)
                        .version(1)
                        .effectiveFrom(updatedEmployee.getHireDate() != null ? updatedEmployee.getHireDate() : LocalDate.now())
                        .isActive(true)
                        .createdBy(getCurrentEmployeeId())
                        .build();
                employeeSalaryProfileRepository.save(newProfile);
            }
        }

        return convertToResponse(updatedEmployee);
    }

    // =====================================================
    // ASSIGN MANAGER BY EMPLOYEE CODE
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
    // GET ALL EMPLOYEES
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
    // GET ORGANIZATION CHART
    // =====================================================
    public List<EmployeeSummaryResponse> getOrganizationChart(Long tenantId) {
        log.debug("Getting organization chart for tenant: {}", tenantId);

        // Get all employees with their managers in one query
        List<Employee> allEmployees = employeeRepository.findByTenant_Id(tenantId);

        // Build hierarchy in memory (only one DB query)
        Map<Long, List<Employee>> managerToSubordinates = new HashMap<>();

        for (Employee emp : allEmployees) {
            Long managerId = emp.getManager() != null ? emp.getManager().getId() : null;
            managerToSubordinates.computeIfAbsent(managerId, k -> new ArrayList<>()).add(emp);
        }

        // Build response for top-level employees (no manager)
        return managerToSubordinates.getOrDefault(null, Collections.emptyList())
                .stream()
                .map(emp -> buildHierarchyResponse(emp, managerToSubordinates))
                .collect(Collectors.toList());
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

    // =====================================================
    // UPDATE EMPLOYEE STATUS
    // =====================================================
    @Transactional
    public void updateEmployeeStatus(@NonNull Long id, @NonNull Long tenantId, EmployeeStatus newStatus,
            String reason) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        EmployeeStatus oldStatus = employee.getStatus();

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
        employeeRepository.save(employee);
        log.info("Employee status updated from {} to {}", oldStatus, newStatus);
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
        employeeRepository.save(employee);

        Long performedBy = com.sonixhr.security.TenantContext.getCurrentUserId();
        auditLogService.log(
            employee.getTenant(),
            "EMPLOYEE_DELETED",
            "status",
            oldStatus,
            EmployeeStatus.TERMINATED.name(),
            performedBy,
            "{\"employeeId\":" + id + "}"
        );

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
    // PRIVATE HELPER METHODS
    // =====================================================

    private Employee buildEmployeeFromRequest(Tenant tenant, EmployeeCreateRequest request, String employeeCode) {
        Department department = null;
        Long departmentId = request.getDepartmentId();
        if (departmentId != null) {
            department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        }

        EmploymentType employmentType = request.getEmploymentType() != null ? request.getEmploymentType()
                : EmploymentType.FULL_TIME;

        String resolvedStateText = request.getStateText();
        IndianState resolvedState = request.getState();
        String country = request.getCountry();
        String city = request.getCity();

        // Apply tenant-level fallbacks if address details are missing
        String address = request.getAddress();
        if (address == null || address.trim().isEmpty()) {
            address = tenant.getOfficeAddress();
        }
        if (city == null || city.trim().isEmpty()) {
            city = tenant.getCity();
        }

        country = com.sonixhr.util.CountryUtils.normalizeAndValidateCountry(country != null && !country.trim().isEmpty() ? country : tenant.getCountry());
        boolean isIndia = "IN".equalsIgnoreCase(country);

        if (isIndia) {
            if (resolvedState == null) {
                resolvedState = tenant.getState();
            }
            validateStateForCountry(resolvedState, country);
            resolvedStateText = null;
        } else {
            resolvedState = null;
            if (resolvedStateText == null || resolvedStateText.trim().isEmpty()) {
                resolvedStateText = tenant.getStateText();
            }
        }

        String postalCode = request.getPostalCode();

        return Employee.builder()
                .tenant(tenant)
                .employeeCode(employeeCode)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .department(department)
                .position(request.getPosition())
                .employmentType(employmentType)
                .workLocation(request.getWorkLocation())
                .address(address)
                .city(city)
                .state(resolvedState)
                .stateText(resolvedStateText)
                .country(country)
                .postalCode(postalCode)
                .hireDate(request.getHireDate() != null ? request.getHireDate() : LocalDate.now())
                .status(EmployeeStatus.INACTIVE)
                .isActive(false)
                .createdBy(getCurrentEmployeeId())
                .weekendConfig(request.getWeekendConfig())
                .customWeekendDays(request.getCustomWeekendDays())
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
                .bankDetails(employee.getBankDetails())
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
            List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepository.findActiveByEmployeeId(employee.getId());
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

    private Employee findEmployeeByIdAndTenant(@NonNull Long id, @NonNull Long tenantId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        if (!employee.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }
        return employee;
    }

    private Long getCurrentEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Employee) {
                return ((Employee) principal).getId();
            }
        }
        return null;
    }

    public void validateManagerAssignment(Employee employee, Employee manager, Long tenantId) {
        if (manager == null) {
            log.info("Removing manager for employee: {}", employee.getEmployeeCode());
            return;
        }

        if (employee.getId().equals(manager.getId())) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Employee cannot be their own manager");
        }

        if (!manager.getTenantId().equals(tenantId)) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Manager must be from the same tenant");
        }

        if (!manager.isActive()) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Manager must be an active employee");
        }

        if (manager.isOnProbation()) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Employees on probation cannot be managers");
        }

        if (isCircularReference(employee, manager)) {
            throw new com.sonixhr.exceptions.ValidationException("managerId", "Circular manager reference detected");
        }
    }

    private boolean isCircularReference(Employee employee, Employee potentialManager) {
        Set<Long> visited = new HashSet<>();
        Employee current = potentialManager;

        while (current != null) {
            if (current.getId().equals(employee.getId())) {
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
    // ADD THESE MISSING METHODS
    // =====================================================

    /**
     * Forgot password - sends reset link to employee email
     */
    @Transactional
    public void forgotPassword(String email) {
        log.info("Forgot password request for email: {}", email);

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));

        if (!employee.isActive()) {
            throw new BusinessException("Cannot reset password for inactive employee");
        }

        // Generate password reset token
        String resetToken = activationTokenService.generatePasswordResetTokenForEmployee(employee.getId());
        String resetLink = baseUrl + "/api/tenant/auth/reset-password?token=" + resetToken;

        // Send reset email
        emailService.sendPasswordResetEmail(employee.getEmail(), employee.getFullName(), resetLink);
        log.info("Password reset email sent to: {}", email);
    }

    /**
     * Reset password using token from email link
     */
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

    /**
     * Activate employee (already exists but ensure it matches)
     */
    @Transactional
    public Employee activateEmployee(String token, String password, String confirmPassword) {
        log.info("Activating employee with token: {}", token);

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
            throw new BusinessException("Invalid or expired activation token");
        }

        // Activate employee
        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setMustChangePassword(true);
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

    /**
     * Resend activation email to employee
     */
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

    /**
     * Change password for authenticated employee
     */
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

    /**
     * Reset password by admin (without old password)
     */
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

    public List<EmployeeDropdownDTO> getActiveEmployeesForDropdown(@NonNull Long tenantId) {
        log.info("Getting active employees for dropdown for tenant: {}", tenantId);
        return employeeRepository.findActiveEmployeesForDropdown(tenantId);
    }

    private void validateStateForCountry(IndianState state, String country) {
        if ("IN".equalsIgnoreCase(country) && state == null) {
            throw new com.sonixhr.exceptions.ValidationException("state", "State is required for employees in India");
        }
    }
}