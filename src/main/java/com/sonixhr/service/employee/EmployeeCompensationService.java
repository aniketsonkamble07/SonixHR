package com.sonixhr.service.employee;

import com.sonixhr.dto.employee.EmployeeCompensationRequest;
import com.sonixhr.dto.employee.EmployeeCompensationResponse;
import com.sonixhr.dto.employee.EmployeeCompensationPeriodResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.EmployeeSalaryComponent;
import com.sonixhr.entity.payroll.EmployeeSalaryProfile;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.EmployeeSalaryComponentRepository;
import com.sonixhr.repository.payroll.EmployeeSalaryProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class EmployeeCompensationService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final com.sonixhr.repository.payroll.TenantSalaryStructureRepository tenantSalaryStructureRepo;

    @Transactional
    public EmployeeCompensationResponse updateCompensation(Long tenantId, Long employeeId, EmployeeCompensationRequest request) {
        log.info("Updating compensation for employee: {} in tenant: {}", employeeId, tenantId);
        Employee employee = findEmployeeByIdAndTenant(employeeId, tenantId);

        // 1. Update bank details if provided
        if (request.getBankDetails() != null) {
            employee.setBankDetails(request.getBankDetails());
            employeeRepository.save(employee);
        }

        // 2. Process salary profile and overrides
        boolean hasSalaryDetails = request.getMonthlyCtc() != null || request.getTaxRegime() != null || request.getCurrency() != null;
        boolean hasOverrides = request.getComponentOverrides() != null;

        if (hasSalaryDetails) {
            BigDecimal monthlyCtc = request.getMonthlyCtc();
            if (monthlyCtc != null && monthlyCtc.compareTo(BigDecimal.ZERO) < 0) {
                throw new com.sonixhr.exceptions.ValidationException("monthlyCtc", "Monthly CTC cannot be negative");
            }

            LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();
            String currency = request.getCurrency() != null ? request.getCurrency() : "INR";
            String taxRegime = request.getTaxRegime() != null ? request.getTaxRegime() : "NEW_REGIME";

            // Validate component overrides against global tenant salary structure active on the effectiveFrom date
            if (hasOverrides) {
                validateOverridesAgainstGlobalConfig(tenantId, effectiveFrom, request.getComponentOverrides());
            }

            // Load all existing profiles sorted ascending by start date
            List<EmployeeSalaryProfile> existingProfiles = employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(employeeId);

            // Assert that no profile with the exact same effectiveFrom already exists
            for (EmployeeSalaryProfile p : existingProfiles) {
                if (p.getEffectiveFrom().isEqual(effectiveFrom)) {
                    throw new com.sonixhr.exceptions.ValidationException("effectiveFrom", "A salary profile with the exact start date " + effectiveFrom + " already exists.");
                }
            }

            // Close existing active profile active on new effective date
            Optional<EmployeeSalaryProfile> existingActiveOpt = employeeSalaryProfileRepo.findActiveByEmployeeAndDate(employeeId, effectiveFrom);
            if (existingActiveOpt.isPresent()) {
                EmployeeSalaryProfile existingActive = existingActiveOpt.get();
                if (existingActive.getEffectiveFrom().isAfter(effectiveFrom) || existingActive.getEffectiveFrom().isEqual(effectiveFrom)) {
                    throw new com.sonixhr.exceptions.ValidationException("effectiveFrom", "New effective date must be after the active salary profile's start date (" + existingActive.getEffectiveFrom() + ")");
                }
                existingActive.setEffectiveTo(effectiveFrom.minusDays(1));
                employeeSalaryProfileRepo.save(existingActive);
            }

            // Find immediate successor profile starting after the new effectiveFrom
            EmployeeSalaryProfile immediateSuccessor = null;
            for (EmployeeSalaryProfile p : existingProfiles) {
                if (p.getEffectiveFrom().isAfter(effectiveFrom)) {
                    immediateSuccessor = p;
                    break;
                }
            }
            LocalDate effectiveTo = null;
            if (immediateSuccessor != null) {
                effectiveTo = immediateSuccessor.getEffectiveFrom().minusDays(1);
            }

            // Create new salary profile
            EmployeeSalaryProfile newProfile = EmployeeSalaryProfile.builder()
                    .tenant(employee.getTenant())
                    .employee(employee)
                    .monthlyCtc(monthlyCtc != null ? monthlyCtc : BigDecimal.ZERO)
                    .currency(currency)
                    .taxRegime(taxRegime)
                    .effectiveFrom(effectiveFrom)
                    .effectiveTo(effectiveTo)
                    .build();
            newProfile = employeeSalaryProfileRepo.save(newProfile);

            // Create component overrides for the new profile
            if (hasOverrides) {
                saveComponentOverrides(newProfile, request.getComponentOverrides());
            }

        } else if (hasOverrides) {
            // No new profile details, so apply overrides to the currently active profile
            LocalDate today = LocalDate.now();
            EmployeeSalaryProfile activeProfile = employeeSalaryProfileRepo.findActiveByEmployeeAndDate(employeeId, today)
                    .orElseThrow(() -> new BusinessException("Cannot apply component overrides: No active salary profile found for the employee. Please create a salary profile first."));

            // Validate component overrides against global tenant salary structure active on activeProfile's start date
            validateOverridesAgainstGlobalConfig(tenantId, activeProfile.getEffectiveFrom(), request.getComponentOverrides());

            // Clear old overrides first
            List<EmployeeSalaryComponent> existingOverrides = employeeSalaryComponentRepo.findBySalaryProfileId(activeProfile.getId());
            employeeSalaryComponentRepo.deleteAll(existingOverrides);

            // Save new overrides
            saveComponentOverrides(activeProfile, request.getComponentOverrides());
        }

        return getCompensation(tenantId, employeeId);
    }

    private void validateOverridesAgainstGlobalConfig(Long tenantId, LocalDate date, List<EmployeeCompensationRequest.ComponentOverrideRequest> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        List<com.sonixhr.entity.payroll.TenantSalaryStructure> structures = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId, date);
        if (structures.isEmpty()) {
            throw new com.sonixhr.exceptions.ValidationException("componentOverrides", "No global salary structures are configured or active for this tenant on " + date + ". Please configure tenant salary structure first.");
        }

        Set<String> allowedComponents = structures.stream()
                .map(s -> s.getComponentCode().toUpperCase())
                .collect(Collectors.toSet());

        for (EmployeeCompensationRequest.ComponentOverrideRequest override : overrides) {
            String componentCode = override.getComponentCode().toUpperCase();
            if (!allowedComponents.contains(componentCode)) {
                throw new com.sonixhr.exceptions.ValidationException("componentOverrides", "Component code '" + componentCode + "' is not allowed or active in global tenant payroll configuration.");
            }
        }
    }

    public EmployeeCompensationResponse getCompensation(Long tenantId, Long employeeId) {
        Employee employee = findEmployeeByIdAndTenant(employeeId, tenantId);
        LocalDate today = LocalDate.now();

        // Get currently active salary profile
        Optional<EmployeeSalaryProfile> activeProfileOpt = employeeSalaryProfileRepo.findActiveByEmployeeAndDate(employeeId, today);
        
        List<EmployeeCompensationResponse.ComponentOverrideInfo> componentOverrides = new ArrayList<>();
        EmployeeCompensationResponse.ActiveSalaryProfileInfo activeProfileInfo = null;

        if (activeProfileOpt.isPresent()) {
            EmployeeSalaryProfile activeProfile = activeProfileOpt.get();
            activeProfileInfo = EmployeeCompensationResponse.ActiveSalaryProfileInfo.builder()
                    .id(activeProfile.getId())
                    .monthlyCtc(activeProfile.getMonthlyCtc())
                    .currency(activeProfile.getCurrency())
                    .taxRegime(activeProfile.getTaxRegime())
                    .effectiveFrom(activeProfile.getEffectiveFrom())
                    .effectiveTo(activeProfile.getEffectiveTo())
                    .build();

            // Fetch component overrides for the active profile
            List<EmployeeSalaryComponent> overrides = employeeSalaryComponentRepo.findBySalaryProfileId(activeProfile.getId());
            componentOverrides = overrides.stream()
                    .map(o -> EmployeeCompensationResponse.ComponentOverrideInfo.builder()
                            .id(o.getId())
                            .componentCode(o.getComponentCode())
                            .amount(o.getOverrideValue())
                            .customExpression(o.getOverrideFormula())
                            .overrideValue(o.getOverrideValue())
                            .overrideType(o.getOverrideType() != null ? o.getOverrideType() : "VALUE")
                            .build())
                    .collect(Collectors.toList());
        }

        // Fetch salary history
        List<EmployeeSalaryProfile> history = employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);

        List<EmployeeCompensationResponse.SalaryProfileHistoryInfo> salaryHistory = history.stream()
                .map(p -> EmployeeCompensationResponse.SalaryProfileHistoryInfo.builder()
                        .id(p.getId())
                        .monthlyCtc(p.getMonthlyCtc())
                        .currency(p.getCurrency())
                        .taxRegime(p.getTaxRegime())
                        .effectiveFrom(p.getEffectiveFrom())
                        .effectiveTo(p.getEffectiveTo())
                        .build())
                .collect(Collectors.toList());

        return EmployeeCompensationResponse.builder()
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .bankDetails(employee.getBankDetails())
                .activeSalaryProfile(activeProfileInfo)
                .componentOverrides(componentOverrides)
                .salaryHistory(salaryHistory)
                .monthlyCtc(activeProfileInfo != null ? activeProfileInfo.getMonthlyCtc() : null)
                .currency(activeProfileInfo != null ? activeProfileInfo.getCurrency() : null)
                .taxRegime(activeProfileInfo != null ? activeProfileInfo.getTaxRegime() : null)
                .effectiveFrom(activeProfileInfo != null ? activeProfileInfo.getEffectiveFrom() : null)
                .effectiveTo(activeProfileInfo != null ? activeProfileInfo.getEffectiveTo() : null)
                .build();
    }

    private void saveComponentOverrides(EmployeeSalaryProfile profile, List<EmployeeCompensationRequest.ComponentOverrideRequest> overrideRequests) {
        for (EmployeeCompensationRequest.ComponentOverrideRequest overrideReq : overrideRequests) {
            BigDecimal value = overrideReq.getOverrideValue() != null ? overrideReq.getOverrideValue() : overrideReq.getAmount();
            String type = overrideReq.getOverrideType() != null ? overrideReq.getOverrideType() : 
                          (overrideReq.getCustomExpression() != null && !overrideReq.getCustomExpression().trim().isEmpty() ? "FORMULA" : "VALUE");
            String formula = overrideReq.getCustomExpression();
            EmployeeSalaryComponent override = EmployeeSalaryComponent.builder()
                    .tenant(profile.getTenant())
                    .salaryProfile(profile)
                    .componentCode(overrideReq.getComponentCode().toUpperCase())
                    .overrideValue(value)
                    .overrideFormula(formula)
                    .overrideType(type)
                    .build();
            employeeSalaryComponentRepo.save(override);
        }
    }

    public EmployeeCompensationPeriodResponse getCompensationForPeriod(Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate) {
        Employee employee = findEmployeeByIdAndTenant(employeeId, tenantId);

        List<EmployeeSalaryProfile> overlappingProfiles = employeeSalaryProfileRepo.findProfilesOverlappingPeriod(employeeId, startDate, endDate);

        List<EmployeeCompensationPeriodResponse.ProfilePeriodInfo> profileInfos = new ArrayList<>();

        for (EmployeeSalaryProfile profile : overlappingProfiles) {
            List<EmployeeSalaryComponent> overrides = employeeSalaryComponentRepo.findBySalaryProfileId(profile.getId());
            List<EmployeeCompensationPeriodResponse.ComponentOverrideInfo> overrideInfos = overrides.stream()
                    .map(o -> EmployeeCompensationPeriodResponse.ComponentOverrideInfo.builder()
                            .id(o.getId())
                            .componentCode(o.getComponentCode())
                            .amount(o.getOverrideValue())
                            .customExpression(o.getOverrideFormula())
                            .overrideValue(o.getOverrideValue())
                            .overrideType(o.getOverrideType() != null ? o.getOverrideType() : "VALUE")
                            .build())
                    .collect(Collectors.toList());

            profileInfos.add(EmployeeCompensationPeriodResponse.ProfilePeriodInfo.builder()
                    .id(profile.getId())
                    .monthlyCtc(profile.getMonthlyCtc())
                    .currency(profile.getCurrency())
                    .taxRegime(profile.getTaxRegime())
                    .effectiveFrom(profile.getEffectiveFrom())
                    .effectiveTo(profile.getEffectiveTo())
                    .componentOverrides(overrideInfos)
                    .build());
        }

        return EmployeeCompensationPeriodResponse.builder()
                .employeeId(employee.getId())
                .activeProfiles(profileInfos)
                .build();
    }

    private Employee findEmployeeByIdAndTenant(Long employeeId, Long tenantId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
        if (!employee.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }
        return employee;
    }
}
