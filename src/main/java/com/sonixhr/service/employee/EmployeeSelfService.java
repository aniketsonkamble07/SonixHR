package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.EmployeeProfileUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.employee.MyOrgChartResponse;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.department.DepartmentRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeSelfService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeService employeeService;

    // =====================================================
    // Update employee profile (self-service)
    // =====================================================
    @Transactional
    public EmployeeResponse updateEmployeeProfile(Long tenantId, String email,
            EmployeeProfileUpdateRequest request) {
        log.info("Updating profile for employee: {}", email);

        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // =====================================================
        // CHECK PERMISSIONS FROM SECURITY CONTEXT
        // =====================================================
        boolean isSuperAdmin = isCurrentUserSuperAdmin();
        boolean isHR = isCurrentUserHR();

        log.debug("User permissions - SuperAdmin: {}, HR: {}", isSuperAdmin, isHR);

        // =====================================================
        // PERSONAL INFORMATION (All employees can update)
        // =====================================================
        updatePersonalInfo(employee, request);

        // =====================================================
        // PROFESSIONAL INFORMATION (Only Super Admin/HR can update)
        // =====================================================
        if (isSuperAdmin || isHR) {
            updateProfessionalInfo(employee, request, tenantId);
        } else if (hasProfessionalInfoRequest(request)) {
            log.warn("Employee {} attempted to update professional fields without permission", email);
            throw new BusinessException(
                    "You don't have permission to update professional information. Only HR or Super Admin can update department, position, work location, and manager.");
        }

        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Profile updated successfully for employee: {}", email);
        return employeeService.convertToResponse(updatedEmployee);
    }

    private void updatePersonalInfo(Employee employee, EmployeeProfileUpdateRequest request) {
        if (request.getPhone() != null)
            employee.setPhone(request.getPhone());
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
        if (request.getAddress() != null)
            employee.setAddress(request.getAddress());
        if (request.getCity() != null)
            employee.setCity(request.getCity());
        if (request.getState() != null)
            employee.setState(request.getState());
        if (request.getCountry() != null)
            employee.setCountry(request.getCountry());
        if (request.getPostalCode() != null)
            employee.setPostalCode(request.getPostalCode());
        if (request.getPermanentAddress() != null)
            employee.setPermanentAddress(request.getPermanentAddress());
        if (request.getEmergencyContactName() != null)
            employee.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null)
            employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        if (request.getEmergencyContactRelation() != null)
            employee.setEmergencyContactRelation(request.getEmergencyContactRelation());
        if (request.getEmergencyContactEmail() != null)
            employee.setEmergencyContactEmail(request.getEmergencyContactEmail());
        if (request.getSecondaryEmergencyName() != null)
            employee.setSecondaryEmergencyName(request.getSecondaryEmergencyName());
        if (request.getSecondaryEmergencyPhone() != null)
            employee.setSecondaryEmergencyPhone(request.getSecondaryEmergencyPhone());
        if (request.getBankDetails() != null)
            employee.setBankDetails(request.getBankDetails());
        if (request.getLinkedinUrl() != null)
            employee.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getGithubUrl() != null)
            employee.setGithubUrl(request.getGithubUrl());
        if (request.getTwitterUrl() != null)
            employee.setTwitterUrl(request.getTwitterUrl());
    }

    private void updateProfessionalInfo(Employee employee, EmployeeProfileUpdateRequest request, Long tenantId) {
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with id: " + request.getDepartmentId()));
            employee.setDepartment(department);
            log.info("Department updated to: {} (ID: {})", department.getName(), department.getId());
        }
        if (request.getPosition() != null) {
            employee.setPosition(request.getPosition());
            log.info("Position updated to: {}", request.getPosition());
        }
        if (request.getWorkLocation() != null) {
            employee.setWorkLocation(request.getWorkLocation());
            log.info("Work location updated to: {}", request.getWorkLocation());
        }
        if (request.getManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Manager not found with id: " + request.getManagerId()));
            employee.setManager(manager);
            log.info("Manager updated to: {} (ID: {})", manager.getFullName(), manager.getId());
        }
    }

    private boolean hasProfessionalInfoRequest(EmployeeProfileUpdateRequest request) {
        return request.getDepartmentId() != null ||
                request.getPosition() != null ||
                request.getWorkLocation() != null ||
                request.getManagerId() != null;
    }

    private boolean isCurrentUserSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Employee) {
            return ((Employee) principal).isSuperAdmin();
        } else if (principal instanceof PlatformUser) {
            return ((PlatformUser) principal).isSuperAdmin();
        }

        // Check if user has SUPER_ADMIN authority
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("SUPER_ADMIN") ||
                        auth.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private boolean isCurrentUserHR() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Employee) {
            Employee emp = (Employee) principal;
            return emp.isSuperAdmin() || emp.isAdmin() || emp.hasRole("HR") || emp.hasRole("Human Resources");
        } else if (principal instanceof PlatformUser) {
            PlatformUser pu = (PlatformUser) principal;
            return pu.isSuperAdmin() || pu.hasRole("ADMIN") || pu.hasRole("HR");
        }

        // Check if user has HR role
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("HR") ||
                        auth.getAuthority().equals("ROLE_HR") ||
                        auth.getAuthority().equals("ADMIN") ||
                        auth.getAuthority().equals("ROLE_ADMIN"));
    }

    // =====================================================
    // Get employee by email (for self-service)
    // =====================================================
    public EmployeeResponse getEmployeeByEmail(Long tenantId, String email) {
        log.debug("Fetching employee by email: {} for tenant: {}", email, tenantId);

        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Employee not found with email: %s", email)));

        return employeeService.convertToResponse(employee);
    }

    // =====================================================
    // Get current employee profile
    // =====================================================
    public EmployeeResponse getCurrentEmployeeProfile(Long tenantId, String email) {
        log.debug("Fetching current employee profile for: {}", email);
        return getEmployeeByEmail(tenantId, email);
    }

    // =====================================================
    // Get personalized organization chart
    // =====================================================
    public MyOrgChartResponse getMyOrgChart(Long tenantId, String email) {
        log.info("Fetching personalized org chart for employee: {}", email);

        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // 1. Current Employee Summary
        EmployeeSummaryResponse selfSummary = employeeService.convertToSummaryResponse(employee);

        // 2. Manager Chain (Traverse up)
        List<EmployeeSummaryResponse> managerChain = new ArrayList<>();
        Employee currentManager = employee.getManager();
        while (currentManager != null) {
            managerChain.add(employeeService.convertToSummaryResponse(currentManager));
            currentManager = currentManager.getManager();
        }

        // 3. Peers (Colleagues reporting to same manager, excluding self)
        List<EmployeeSummaryResponse> peers = new ArrayList<>();
        if (employee.getManager() != null) {
            peers = employeeRepository.findByManagerIdAndTenantId(employee.getManager().getId(), tenantId)
                    .stream()
                    .filter(peer -> !peer.getId().equals(employee.getId()))
                    .map(employeeService::convertToSummaryResponse)
                    .collect(Collectors.toList());
        }

        // 4. Direct Reports
        List<EmployeeSummaryResponse> directReports = employeeRepository
                .findByManagerIdAndTenantId(employee.getId(), tenantId)
                .stream()
                .map(employeeService::convertToSummaryResponse)
                .collect(Collectors.toList());

        return MyOrgChartResponse.builder()
                .employee(selfSummary)
                .managerChain(managerChain)
                .peers(peers)
                .directReports(directReports)
                .build();
    }

}