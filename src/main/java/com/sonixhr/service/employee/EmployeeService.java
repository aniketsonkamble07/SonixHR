package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSearchResponse;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.enums.employee.EmploymentType;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.department.DepartmentRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
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
import java.util.List;
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

    @Value("${app.base-url}")
    private String baseUrl;

    // =====================================================
    // CREATE EMPLOYEE
    // =====================================================
    @Transactional
    public EmployeeResponse createEmployee(Long tenantId, EmployeeCreateRequest request) {  // ✅ Changed to Long
        log.info("Creating employee for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        // Check if employee with this email already exists in this tenant
        if (employeeRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("Employee with email " + request.getEmail() + " already exists in this tenant");
        }

        // Generate employee code
        String employeeCode = employeeCodeGenerator.generateEmployeeCode(tenant);
        // Create employee entity directly
        Employee employee = buildEmployeeFromRequest(tenant, request, employeeCode);

        // Set manager if provided
        if (request.getManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + request.getManagerId()));
            if (!manager.getTenantId().equals(tenantId)) {
                throw new BusinessException("Manager must be from the same tenant");
            }
            employee.setManager(manager);
        }

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with code: {}", employeeCode);

        // Generate activation token for employee
        String activationTokenValue = activationTokenService.generateTokenForEmployee(savedEmployee.getId());
        String activationLink = baseUrl + "/api/employee/auth/activate?token=" + activationTokenValue;

        // Send activation email
        emailService.sendEmployeeActivationEmail(
                savedEmployee.getEmail(),
                savedEmployee.getFirstName(),
                activationLink
        );

        return convertToResponse(savedEmployee);
    }

    // =====================================================
    // ACTIVATE EMPLOYEE (Set password)
    // =====================================================
    @Transactional
    public Employee activateEmployee(String token, String password, String confirmPassword) {
        log.info("Activating employee with token: {}", token);

        if (!password.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        validatePasswordStrength(password);

        // Get employee ID from token and set password
        Long employeeId = activationTokenService.getEmployeeIdFromToken(token);
        activationTokenService.invalidateToken(token);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);

        return employeeRepository.save(employee);
    }

    // =====================================================
    // UPDATE EMPLOYEE
    // =====================================================
    @Transactional
    public EmployeeResponse updateEmployee(Long id, Long tenantId, EmployeeCreateRequest request) {  // ✅ Changed to Long
        log.info("Updating employee with id: {} for tenant: {}", id, tenantId);

        Employee employee = findEmployeeByIdAndTenant(id, tenantId);

        // Update personal information
        if (request.getFirstName() != null) employee.setFirstName(request.getFirstName());
        if (request.getLastName() != null) employee.setLastName(request.getLastName());
        if (request.getEmail() != null) employee.setEmail(request.getEmail());
        if (request.getPhone() != null) employee.setPhone(request.getPhone());

        // Update professional information
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            employee.setDepartment(department);
        }
        if (request.getPosition() != null) employee.setPosition(request.getPosition());

        if (request.getEmploymentType() != null) {
            employee.setEmploymentType(request.getEmploymentType());
        }
        if (request.getWorkLocation() != null) employee.setWorkLocation(request.getWorkLocation());
        if (request.getHireDate() != null) employee.setHireDate(request.getHireDate());
        if (request.getProbationMonths() != null) employee.setProbationMonths(request.getProbationMonths());

        // Update manager if changed
        if (request.getManagerId() != null && (employee.getManager() == null ||
                !employee.getManager().getId().equals(request.getManagerId()))) {
            Employee newManager = findEmployeeByIdAndTenant(request.getManagerId(), tenantId);
            validateManagerAssignment(employee, newManager, tenantId);
            employee.setManager(newManager);
        }

        employee.setUpdatedBy(getCurrentEmployeeId());
        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", id);

        return convertToResponse(updatedEmployee);
    }

    // =====================================================
    // ASSIGN MANAGER BY EMPLOYEE CODE
    // =====================================================
    @Transactional
    public EmployeeResponse assignManagerByCode(String employeeCode, String managerCode, Long tenantId, String reason) {  // ✅ Changed to Long
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
    public void removeManager(String employeeCode, Long tenantId, String reason) {  // ✅ Changed to Long
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
    public Page<EmployeeResponse> getTeamMembersPaginated(Long tenantId, Long managerId, Pageable pageable) {  // ✅ Changed to Long
        log.info("Getting team members for manager: {} with pagination", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        return employeeRepository.findByManagerIdAndTenant_Id(managerId, tenantId, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET TEAM MEMBERS (List)
    // =====================================================
    public List<EmployeeResponse> getTeamMembers(Long managerId, Long tenantId) {  // ✅ Changed to Long
        log.info("Getting team members for manager: {}", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        return employeeRepository.findByManagerIdAndTenant_Id(managerId, tenantId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET ALL SUBORDINATES
    // =====================================================
    public List<EmployeeResponse> getAllSubordinates(Long managerId, Long tenantId) {  // ✅ Changed to Long
        log.info("Getting all subordinates for manager: {}", managerId);
        findEmployeeByIdAndTenant(managerId, tenantId);
        List<Employee> allSubordinates = new java.util.ArrayList<>();
        collectAllSubordinates(managerId, tenantId, allSubordinates);
        return allSubordinates.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private void collectAllSubordinates(Long managerId, Long tenantId, List<Employee> result) {  // ✅ Changed to Long
        List<Employee> directReports = employeeRepository.findByManagerIdAndTenant_Id(managerId, tenantId);
        result.addAll(directReports);
        for (Employee report : directReports) {
            collectAllSubordinates(report.getId(), tenantId, result);
        }
    }

    // =====================================================
    // GET MANAGER CHAIN
    // =====================================================
    public List<EmployeeResponse> getManagerChain(Long employeeId, Long tenantId) {  // ✅ Changed to Long
        log.info("Getting manager chain for employee: {}", employeeId);
        Employee employee = findEmployeeByIdAndTenant(employeeId, tenantId);
        List<Employee> chain = new java.util.ArrayList<>();
        Employee current = employee.getManager();
        while (current != null) {
            chain.add(current);
            current = current.getManager();
        }
        return chain.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET EMPLOYEES WITH NO MANAGER
    // =====================================================
    public List<EmployeeResponse> getEmployeesWithNoManager(Long tenantId) {  // ✅ Changed to Long
        log.info("Getting employees with no manager for tenant: {}", tenantId);
        return employeeRepository.findByManagerIsNullAndTenant_Id(tenantId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // CHECK IF EMPLOYEE IS MANAGER
    // =====================================================
    public boolean isManager(Long employeeId, Long tenantId) {  // ✅ Changed to Long
        log.info("Checking if employee is manager: {}", employeeId);
        findEmployeeByIdAndTenant(employeeId, tenantId);
        long directReportsCount = employeeRepository.countByManagerIdAndTenant_Id(employeeId, tenantId);
        return directReportsCount > 0;
    }

    // =====================================================
    // SEARCH EMPLOYEES FOR ASSIGNMENT
    // =====================================================
    public Page<EmployeeSearchResponse> searchEmployeesForAssignment(Long tenantId, String query, Pageable pageable) {  // ✅ Changed to Long
        log.info("Searching employees for assignment with query: {}", query);
        return employeeRepository.searchEmployeesForAssignment(tenantId, query, pageable)
                .map(this::convertToSearchResponse);
    }

    // =====================================================
    // GET EMPLOYEE BY ID
    // =====================================================
    public EmployeeResponse getEmployeeById(Long id, Long tenantId) {  // ✅ Changed to Long
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY CODE
    // =====================================================
    public EmployeeResponse getEmployeeByCode(Long tenantId, String employeeCode) {  // ✅ Changed to Long
        Employee employee = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY EMAIL
    // =====================================================
    public EmployeeResponse getEmployeeByEmail(Long tenantId, String email) {  // ✅ Changed to Long
        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET ALL EMPLOYEES
    // =====================================================
    public Page<EmployeeResponse> getAllEmployees(Long tenantId, Pageable pageable) {  // ✅ Changed to Long
        return employeeRepository.findByTenant_Id(tenantId, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY STATUS
    // =====================================================
    public Page<EmployeeResponse> getEmployeesByStatus(Long tenantId, EmployeeStatus status, Pageable pageable) {  // ✅ Changed to Long
        return employeeRepository.findByTenant_IdAndStatus(tenantId, status, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY DEPARTMENT NAME
    // =====================================================
    public List<EmployeeResponse> getEmployeesByDepartmentName(Long tenantId, String departmentName) {  // ✅ Changed to Long
        log.debug("Fetching employees in department: {} for tenant: {}", departmentName, tenantId);
        return employeeRepository.findByTenantIdAndDepartmentName(tenantId, departmentName)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET ORGANIZATION CHART
    // =====================================================
    public List<EmployeeResponse> getOrganizationChart(Long tenantId) {  // ✅ Changed to Long
        List<Employee> allEmployees = employeeRepository.findByTenant_Id(tenantId);
        return allEmployees.stream()
                .filter(e -> e.getManager() == null)
                .map(this::convertToResponseWithChildren)
                .collect(Collectors.toList());
    }

    // =====================================================
    // UPDATE EMPLOYEE STATUS
    // =====================================================
    @Transactional
    public void updateEmployeeStatus(Long id, Long tenantId, EmployeeStatus newStatus, String reason) {  // ✅ Changed to Long
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
    public void confirmEmployee(Long id, Long tenantId) {  // ✅ Changed to Long
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
    public Page<EmployeeResponse> searchEmployees(Long tenantId, String searchTerm, Pageable pageable) {  // ✅ Changed to Long
        return employeeRepository.searchEmployees(tenantId, searchTerm, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // DELETE EMPLOYEE (SOFT DELETE)
    // =====================================================
    @Transactional
    public void deleteEmployee(Long id, Long tenantId) {  // ✅ Changed to Long
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        List<Employee> subordinates = employeeRepository.findByManagerIdAndTenant_Id(id, tenantId);
        if (!subordinates.isEmpty()) {
            throw new BusinessException("Cannot delete employee with " + subordinates.size() + " subordinates");
        }
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setLastWorkingDate(LocalDate.now());
        employee.setActive(false);
        employeeRepository.save(employee);
        log.info("Employee soft deleted successfully: {}", id);
    }

    // =====================================================
    // GET DEPARTMENT STATISTICS
    // =====================================================
    public List<DepartmentStat> getDepartmentStatistics(Long tenantId) {  // ✅ Changed to Long
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
    public List<EmployeeResponse> getUpcomingBirthdays(Long tenantId, int days) {  // ✅ Changed to Long
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeRepository.findByTenant_Id(tenantId);
        return employees.stream()
                .filter(e -> e.getDateOfBirth() != null)
                .filter(e -> {
                    LocalDate nextBirthday = e.getDateOfBirth().withYear(today.getYear());
                    if (nextBirthday.isBefore(today)) {
                        nextBirthday = nextBirthday.plusYears(1);
                    }
                    long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, nextBirthday);
                    return daysUntil >= 0 && daysUntil <= days;
                })
                .sorted((e1, e2) -> {
                    LocalDate b1 = e1.getDateOfBirth().withYear(today.getYear());
                    LocalDate b2 = e2.getDateOfBirth().withYear(today.getYear());
                    if (b1.isBefore(today)) b1 = b1.plusYears(1);
                    if (b2.isBefore(today)) b2 = b2.plusYears(1);
                    return b1.compareTo(b2);
                })
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET UPCOMING ANNIVERSARIES
    // =====================================================
    public List<EmployeeResponse> getUpcomingAnniversaries(Long tenantId, int days) {  // ✅ Changed to Long
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeRepository.findByTenant_Id(tenantId);
        return employees.stream()
                .filter(e -> e.getHireDate() != null)
                .filter(e -> {
                    LocalDate anniversary = e.getHireDate().withYear(today.getYear());
                    if (anniversary.isBefore(today)) {
                        anniversary = anniversary.plusYears(1);
                    }
                    long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, anniversary);
                    return daysUntil >= 0 && daysUntil <= days;
                })
                .sorted((e1, e2) -> {
                    LocalDate a1 = e1.getHireDate().withYear(today.getYear());
                    LocalDate a2 = e2.getHireDate().withYear(today.getYear());
                    if (a1.isBefore(today)) a1 = a1.plusYears(1);
                    if (a2.isBefore(today)) a2 = a2.plusYears(1);
                    return a1.compareTo(a2);
                })
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private Employee buildEmployeeFromRequest(Tenant tenant, EmployeeCreateRequest request, String employeeCode) {
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        }

        EmploymentType employmentType = request.getEmploymentType() != null ?
                request.getEmploymentType() : EmploymentType.FULL_TIME;

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
                .hireDate(request.getHireDate() != null ? request.getHireDate() : LocalDate.now())
                .probationMonths(request.getProbationMonths() != null ? request.getProbationMonths() : 3)
                .status(EmployeeStatus.PROBATION)
                .isActive(false)
                .createdBy(getCurrentEmployeeId())
                .build();
    }

    private EmployeeResponse convertToResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .tenantId(employee.getTenantId())
                .employeeCode(employee.getEmployeeCode())
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
                .department(employee.getDepartment() != null ?
                        EmployeeResponse.DepartmentInfo.builder()
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
                        .department(employee.getManager().getDepartment() != null ?
                                employee.getManager().getDepartment().getName() : null)
                        .employeeCode(employee.getManager().getEmployeeCode())
                        .build() : null)
                .employmentType(employee.getEmploymentType())
                .workLocation(employee.getWorkLocation())
                .hireDate(employee.getHireDate())
                .probationMonths(employee.getProbationMonths())
                .confirmationDate(employee.getConfirmationDate())
                .resignationDate(employee.getResignationDate())
                .lastWorkingDate(employee.getLastWorkingDate())
                .tenureInMonths(employee.getTenureInMonths())
                .status(employee.getStatus())
                .isActive(employee.isActive())
                .address(employee.getAddress())
                .city(employee.getCity())
                .state(employee.getState())
                .country(employee.getCountry())
                .postalCode(employee.getPostalCode())
                .permanentAddress(employee.getPermanentAddress())
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
                .customFields(employee.getCustomFields())
                .linkedinUrl(employee.getLinkedinUrl())
                .githubUrl(employee.getGithubUrl())
                .twitterUrl(employee.getTwitterUrl())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .createdBy(employee.getCreatedBy())
                .updatedBy(employee.getUpdatedBy())
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

    private EmployeeResponse convertToResponseWithChildren(Employee employee) {
        EmployeeResponse response = convertToResponse(employee);
        List<Employee> directReports = employeeRepository.findByManagerIdAndTenant_Id(employee.getId(), employee.getTenantId());
        response.setDirectReports(directReports.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return response;
    }

    private Employee findEmployeeByIdAndTenant(Long id, Long tenantId) {  // ✅ Changed to Long
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

    private void validateManagerAssignment(Employee employee, Employee manager, Long tenantId) {  // ✅ Changed to Long
        if (manager == null) {
            log.info("Removing manager for employee: {}", employee.getEmployeeCode());
            return;
        }

        if (employee.getId().equals(manager.getId())) {
            throw new BusinessException("Employee cannot be their own manager");
        }

        if (!manager.getTenantId().equals(tenantId)) {
            throw new BusinessException("Manager must be from the same tenant");
        }

        if (!manager.isActive()) {
            throw new BusinessException("Manager must be an active employee");
        }

        if (manager.isOnProbation()) {
            throw new BusinessException("Employees on probation cannot be managers");
        }

        if (isCircularReference(employee, manager)) {
            throw new BusinessException("Circular manager reference detected");
        }
    }

    private boolean isCircularReference(Employee employee, Employee potentialManager) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
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
}