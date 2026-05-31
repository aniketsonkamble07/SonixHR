package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.DepartmentStat;
import com.sonixhr.dto.employee.EmployeeCreateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.entity.User;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.UserType;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.UserRepository;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    // =====================================================
    // CREATE EMPLOYEE
    // =====================================================
    @Transactional
    public EmployeeResponse createEmployee(UUID tenantId, EmployeeCreateRequest request) {
        log.info("Creating employee for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        if (employeeRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("Employee with email " + request.getEmail() + " already exists in this tenant");
        }

        // Create User
        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .passwordHash("")
                .fullName(request.getFirstName() + " " + request.getLastName())
                .isActive(false)
                .build();
        User savedUser = userRepository.save(user);
        log.info("User account created for employee: {}", savedUser.getEmail());

        // Generate employee code
        String employeeCode = employeeCodeGenerator.generateSequentialCode(tenant);

        // Create employee entity
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

        employee.setUser(savedUser);
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with code: {}", employeeCode);

        // Generate activation token
        String activationTokenValue = activationTokenService.generateToken(savedUser.getId(), UserType.EMPLOYEE);
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
    // UPDATE EMPLOYEE
    // =====================================================
    @Transactional
    public EmployeeResponse updateEmployee(Long id, UUID tenantId, EmployeeCreateRequest request) {
        log.info("Updating employee with id: {} for tenant: {}", id, tenantId);

        Employee employee = findEmployeeByIdAndTenant(id, tenantId);

        // Update personal information
        if (request.getFirstName() != null) employee.setFirstName(request.getFirstName());
        if (request.getLastName() != null) employee.setLastName(request.getLastName());
        if (request.getEmail() != null) employee.setEmail(request.getEmail());
        if (request.getPhone() != null) employee.setPhone(request.getPhone());

        // Update professional information - FIXED: Use departmentId to fetch Department
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            employee.setDepartment(department);
        }
        if (request.getPosition() != null) employee.setPosition(request.getPosition());
        if (request.getEmploymentType() != null) employee.setEmploymentType(request.getEmploymentType());
        if (request.getWorkLocation() != null) employee.setWorkLocation(request.getWorkLocation());
        if (request.getHireDate() != null) employee.setHireDate(request.getHireDate());
        if (request.getProbationMonths() != null) employee.setProbationMonths(request.getProbationMonths());

        // Update manager if changed
        if (request.getManagerId() != null && (employee.getManager() == null ||
                !employee.getManager().getId().equals(request.getManagerId()))) {
            Employee newManager = findEmployeeByIdAndTenant(request.getManagerId(), tenantId);
            employee.setManager(newManager);
        }

        employee.setUpdatedBy(getCurrentUserId());
        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", id);

        return convertToResponse(updatedEmployee);
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private Employee buildEmployeeFromRequest(Tenant tenant, EmployeeCreateRequest request, String employeeCode) {
        // Fetch Department if departmentId is provided
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        return Employee.builder()
                .tenant(tenant)
                .employeeCode(employeeCode)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .department(department)  // FIXED: Use fetched department
                .position(request.getPosition())
                .hireDate(request.getHireDate())
                .phone(request.getPhone())
                .workLocation(request.getWorkLocation())
                .employmentType(request.getEmploymentType())
                .probationMonths(request.getProbationMonths())
                .status(EmployeeStatus.PROBATION)
                .createdBy(getCurrentUserId())
                .build();
    }

    private EmployeeResponse convertToResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .tenantId(employee.getTenantId())
                .employeeCode(employee.getEmployeeCode())
                .userId(employee.getUserId())
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

    private EmployeeResponse convertToResponseWithChildren(Employee employee) {
        EmployeeResponse response = convertToResponse(employee);
        List<Employee> directReports = employeeRepository.findByManagerIdAndTenant_Id(employee.getId(), employee.getTenantId());
        response.setDirectReports(directReports.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return response;
    }

    // =====================================================
    // GET EMPLOYEE BY ID
    // =====================================================
    public EmployeeResponse getEmployeeById(Long id, UUID tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY CODE
    // =====================================================
    public EmployeeResponse getEmployeeByCode(UUID tenantId, String employeeCode) {
        Employee employee = employeeRepository.findByTenant_IdAndEmployeeCode(tenantId, employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET EMPLOYEE BY EMAIL
    // =====================================================
    public EmployeeResponse getEmployeeByEmail(UUID tenantId, String email) {
        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        return convertToResponse(employee);
    }

    // =====================================================
    // GET ALL EMPLOYEES
    // =====================================================
    public Page<EmployeeResponse> getAllEmployees(UUID tenantId, Pageable pageable) {
        return employeeRepository.findByTenant_Id(tenantId, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY STATUS
    // =====================================================
    public Page<EmployeeResponse> getEmployeesByStatus(UUID tenantId, EmployeeStatus status, Pageable pageable) {
        return employeeRepository.findByTenant_IdAndStatus(tenantId, status, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET EMPLOYEES BY DEPARTMENT
    // =====================================================
    public List<EmployeeResponse> getEmployeesByDepartmentName(UUID tenantId, String departmentName) {
        log.debug("Fetching employees in department: {} for tenant: {}", departmentName, tenantId);

        return employeeRepository.findByTenantIdAndDepartmentName(tenantId, departmentName)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET TEAM MEMBERS
    // =====================================================
    public Page<EmployeeResponse> getTeamMembers(UUID tenantId, Long managerId, Pageable pageable) {
        findEmployeeByIdAndTenant(managerId, tenantId);
        return employeeRepository.findByManagerIdAndTenant_Id(managerId, tenantId, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // GET ORGANIZATION CHART
    // =====================================================
    public List<EmployeeResponse> getOrganizationChart(UUID tenantId) {
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
    public void updateEmployeeStatus(Long id, UUID tenantId, EmployeeStatus newStatus, String reason) {
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
    public void confirmEmployee(Long id, UUID tenantId) {
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
    public Page<EmployeeResponse> searchEmployees(UUID tenantId, String searchTerm, Pageable pageable) {
        return employeeRepository.searchEmployees(tenantId, searchTerm, pageable)
                .map(this::convertToResponse);
    }

    // =====================================================
    // DELETE EMPLOYEE (SOFT DELETE)
    // =====================================================
    @Transactional
    public void deleteEmployee(Long id, UUID tenantId) {
        Employee employee = findEmployeeByIdAndTenant(id, tenantId);
        List<Employee> subordinates = employeeRepository.findByManagerIdAndTenant_Id(id, tenantId);
        if (!subordinates.isEmpty()) {
            throw new BusinessException("Cannot delete employee with " + subordinates.size() + " subordinates");
        }
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setLastWorkingDate(LocalDate.now());
        employeeRepository.save(employee);
        log.info("Employee deleted successfully: {}", id);
    }

    // =====================================================
    // GET DEPARTMENT STATISTICS
    // =====================================================
    public List<DepartmentStat> getDepartmentStatistics(UUID tenantId) {
        List<Object[]> results = employeeRepository.countEmployeesByDepartment(tenantId);
        return results.stream()
                .map(row -> DepartmentStat.builder()
                        .department((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    // =====================================================
    // BULK OPERATIONS
    // =====================================================
    @Transactional
    public void bulkUpdateManager(UUID tenantId, List<Long> employeeIds, Long newManagerId) {
        findEmployeeByIdAndTenant(newManagerId, tenantId);
        int updatedCount = employeeRepository.bulkAssignManager(tenantId, employeeIds, newManagerId);
        log.info("Bulk manager update completed. Updated {} employees", updatedCount);
    }

    @Transactional
    public void bulkUpdateStatus(UUID tenantId, List<Long> employeeIds, EmployeeStatus newStatus) {
        int updatedCount = employeeRepository.bulkUpdateEmployeeStatus(tenantId, employeeIds, newStatus);
        log.info("Bulk status update completed. Updated {} employees", updatedCount);
    }

    // =====================================================
    // GET UPCOMING BIRTHDAYS
    // =====================================================
    public List<EmployeeResponse> getUpcomingBirthdays(UUID tenantId, int days) {
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
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET UPCOMING ANNIVERSARIES
    // =====================================================
    public List<EmployeeResponse> getUpcomingAnniversaries(UUID tenantId, int days) {
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
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private Employee findEmployeeByIdAndTenant(Long id, UUID tenantId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        if (!employee.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }
        return employee;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                // User ID is UUID, but Employee.createdBy expects Long
                // You need to get the Employee ID, not User ID
                Optional<Employee> employee = employeeRepository.findByEmail(userDetails.getUsername());
                return employee.map(Employee::getId).orElse(null);
            }
        }
        return null;
    }
}