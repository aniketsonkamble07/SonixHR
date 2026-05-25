package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.EmployeeProfileUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeSelfService {

    private final EmployeeRepository employeeRepository;

    // =====================================================
    // Update employee profile (self-service)
    // =====================================================
    @Transactional
    public EmployeeResponse updateEmployeeProfile(UUID tenantId, String email,
                                                  EmployeeProfileUpdateRequest request) {
        log.info("Updating profile for employee: {}", email);

        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // Update only null or provided fields
        if (request.getPhone() != null) {
            employee.setPhone(request.getPhone());
        }
        if (request.getPersonalEmail() != null) {
            employee.setPersonalEmail(request.getPersonalEmail());
        }
        if (request.getAddress() != null) {
            employee.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            employee.setCity(request.getCity());
        }
        if (request.getState() != null) {
            employee.setState(request.getState());
        }
        if (request.getCountry() != null) {
            employee.setCountry(request.getCountry());
        }
        if (request.getPostalCode() != null) {
            employee.setPostalCode(request.getPostalCode());
        }
        if (request.getPermanentAddress() != null) {
            employee.setPermanentAddress(request.getPermanentAddress());
        }
        if (request.getEmergencyContactName() != null) {
            employee.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        if (request.getEmergencyContactRelation() != null) {
            employee.setEmergencyContactRelation(request.getEmergencyContactRelation());
        }
        if (request.getEmergencyContactEmail() != null) {
            employee.setEmergencyContactEmail(request.getEmergencyContactEmail());
        }
        if (request.getSecondaryEmergencyName() != null) {
            employee.setSecondaryEmergencyName(request.getSecondaryEmergencyName());
        }
        if (request.getSecondaryEmergencyPhone() != null) {
            employee.setSecondaryEmergencyPhone(request.getSecondaryEmergencyPhone());
        }
        if (request.getLinkedinUrl() != null) {
            employee.setLinkedinUrl(request.getLinkedinUrl());
        }
        if (request.getGithubUrl() != null) {
            employee.setGithubUrl(request.getGithubUrl());
        }
        if (request.getTwitterUrl() != null) {
            employee.setTwitterUrl(request.getTwitterUrl());
        }

        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Profile updated successfully for employee: {}", email);
        return convertToResponse(updatedEmployee);
    }

    // =====================================================
    // Get employee by email (for self-service)
    // =====================================================
    public EmployeeResponse getEmployeeByEmail(UUID tenantId, String email) {
        log.debug("Fetching employee by email: {} for tenant: {}", email, tenantId);

        Employee employee = employeeRepository.findByTenant_IdAndEmail(tenantId, email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Employee not found with email: %s", email)));

        return convertToResponse(employee);
    }

    // =====================================================
    // PRIVATE HELPER METHOD - Convert Employee to Response
    // =====================================================

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
                // FIXED: Convert Department entity to DepartmentInfo DTO
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
}