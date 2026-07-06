package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.EmployeeProfileUpdateRequest;
import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.employee.MyOrgChartResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class EmployeeSelfService {

    private final EmployeeRepository employeeRepository;
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
        // PERSONAL INFORMATION (All employees can update)
        // =====================================================
        updatePersonalInfo(employee, request);

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
        if (request.getStateText() != null)
            employee.setStateText(request.getStateText());
        if (request.getCountry() != null)
            employee.setCountry(com.sonixhr.util.CountryUtils.normalizeAndValidateCountry(request.getCountry()));
        if (request.getPostalCode() != null)
            employee.setPostalCode(request.getPostalCode());
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
        if (request.getLinkedinUrl() != null)
            employee.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getGithubUrl() != null)
            employee.setGithubUrl(request.getGithubUrl());
        if (request.getTwitterUrl() != null)
            employee.setTwitterUrl(request.getTwitterUrl());

        // Apply country-specific validation and cleanup
        if ("IN".equalsIgnoreCase(employee.getCountry())) {
            if (employee.getState() == null) {
                throw new com.sonixhr.exceptions.ValidationException("state", "State is required for employees in India");
            }
            employee.setStateText(null);
        } else {
            employee.setState(null);
        }
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