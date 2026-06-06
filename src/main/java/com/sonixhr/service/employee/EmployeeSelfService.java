package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.EmployeeProfileUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.entity.department.Department;
import com.sonixhr.entity.employee.Employee;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeSelfService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

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
            throw new BusinessException("You don't have permission to update professional information. Only HR or Super Admin can update department, position, work location, and manager.");
        }

        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Profile updated successfully for employee: {}", email);
        return convertToResponse(updatedEmployee);
    }

    private void updatePersonalInfo(Employee employee, EmployeeProfileUpdateRequest request) {
        if (request.getPhone() != null) employee.setPhone(request.getPhone());
        if (request.getDateOfBirth() != null) employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) employee.setGender(request.getGender());
        if (request.getMaritalStatus() != null) employee.setMaritalStatus(request.getMaritalStatus());
        if (request.getBloodGroup() != null) employee.setBloodGroup(request.getBloodGroup());
        if (request.getNationality() != null) employee.setNationality(request.getNationality());
        if (request.getPersonalEmail() != null) employee.setPersonalEmail(request.getPersonalEmail());
        if (request.getAddress() != null) employee.setAddress(request.getAddress());
        if (request.getCity() != null) employee.setCity(request.getCity());
        if (request.getState() != null) employee.setState(request.getState());
        if (request.getCountry() != null) employee.setCountry(request.getCountry());
        if (request.getPostalCode() != null) employee.setPostalCode(request.getPostalCode());
        if (request.getPermanentAddress() != null) employee.setPermanentAddress(request.getPermanentAddress());
        if (request.getEmergencyContactName() != null) employee.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        if (request.getEmergencyContactRelation() != null) employee.setEmergencyContactRelation(request.getEmergencyContactRelation());
        if (request.getEmergencyContactEmail() != null) employee.setEmergencyContactEmail(request.getEmergencyContactEmail());
        if (request.getSecondaryEmergencyName() != null) employee.setSecondaryEmergencyName(request.getSecondaryEmergencyName());
        if (request.getSecondaryEmergencyPhone() != null) employee.setSecondaryEmergencyPhone(request.getSecondaryEmergencyPhone());
        if (request.getBankDetails() != null) employee.setBankDetails(request.getBankDetails());
        if (request.getLinkedinUrl() != null) employee.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getGithubUrl() != null) employee.setGithubUrl(request.getGithubUrl());
        if (request.getTwitterUrl() != null) employee.setTwitterUrl(request.getTwitterUrl());
        if (request.getCustomFields() != null) employee.setCustomFields(request.getCustomFields());
    }

    private void updateProfessionalInfo(Employee employee, EmployeeProfileUpdateRequest request, Long tenantId) {
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
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
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + request.getManagerId()));
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

        return convertToResponse(employee);
    }

    // =====================================================
    // Get current employee profile
    // =====================================================
    public EmployeeResponse getCurrentEmployeeProfile(Long tenantId, String email) {
        log.debug("Fetching current employee profile for: {}", email);
        return getEmployeeByEmail(tenantId, email);
    }

    // =====================================================
    // PRIVATE HELPER METHOD - Convert Employee to Response
    // =====================================================

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
}