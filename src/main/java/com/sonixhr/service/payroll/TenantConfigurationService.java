package com.sonixhr.service.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.payroll.*;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.lang.NonNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class TenantConfigurationService {

    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
    private final SalaryComponentDefinitionRepository componentDefinitionRepo;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final EmployeeSalaryProfileHistoryRepository profileHistoryRepo;
    private final SandboxedSpELEngine spelEngine;
    private final ObjectMapper objectMapper;

    // ============ Tenant Global Configuration ============

    @Transactional
    public TenantPayrollConfigResponse createOrUpdateGlobalConfig(@NonNull Long tenantId, @NonNull TenantPayrollConfigRequest request) {
        log.info("Creating/updating global payroll config for tenant: {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();

        // Validate LOP basis
        if (LopBasis.WORKING_DAYS.equals(request.getLopBasis()) && request.getWorkingDaysPerMonth() == null) {
            throw new BusinessException("Working days per month is required when LOP basis is WORKING_DAYS");
        }

        // Deactivate existing active config
        List<TenantPayrollConfig> activeConfigs = tenantPayrollConfigRepo.findActiveByTenant(tenantId);
        for (TenantPayrollConfig active : activeConfigs) {
            active.setActive(false);
            active.setEffectiveTo(effectiveFrom.minusDays(1));
            tenantPayrollConfigRepo.save(active);
        }

        // Create new config
        TenantPayrollConfig newConfig = TenantPayrollConfig.builder()
                .tenant(tenant)
                .lopBasis(request.getLopBasis())
                .workingDaysPerMonth(request.getWorkingDaysPerMonth())
                .enablePfCapping(request.isEnablePfCapping())
                .enableEsi(request.isEnableEsi())
                .enablePt(request.isEnablePt())
                .enforceNewLabourCodes(request.isEnforceNewLabourCodes())
                .enableOvertime(request.isEnableOvertime())
                .overtimeRatePerHour(request.getOvertimeRatePerHour())
                .defaultCurrency(request.getDefaultCurrency() != null ? request.getDefaultCurrency() : "INR")
                .defaultTaxRegime(request.getDefaultTaxRegime() != null ? request.getDefaultTaxRegime() : "OLD_REGIME")
                .effectiveFrom(effectiveFrom)
                .effectiveTo(null)
                .isActive(true)
                .build();

        TenantPayrollConfig savedConfig = tenantPayrollConfigRepo.save(newConfig);

        // Process salary structures if provided
        if (request.getSalaryStructures() != null && !request.getSalaryStructures().isEmpty()) {
            for (TenantPayrollConfigRequest.SalaryStructureRequest structureReq : request.getSalaryStructures()) {
                // Validate formula if provided
                if ("FORMULA".equals(structureReq.getCalculationType()) && structureReq.getFormulaExpression() != null) {
                    validateFormula(structureReq.getFormulaExpression());
                }

                SalaryComponentDefinition definition = SalaryComponentDefinition.builder()
                        .tenant(tenant)
                        .tenantPayrollConfigId(Objects.requireNonNull(savedConfig.getId()))
                        .componentCode(structureReq.getComponentCode().toUpperCase())
                        .componentName(structureReq.getComponentName())
                        .componentType(structureReq.getComponentType().toUpperCase())
                        .calculationType(structureReq.getCalculationType().toUpperCase())
                        .defaultValue(structureReq.getValue())
                        .formulaExpression(structureReq.getFormulaExpression())
                        .evaluationOrder(structureReq.getEvaluationOrder() != null ? structureReq.getEvaluationOrder() : 5)
                        .isLopApplicable(structureReq.isLopApplicable())
                        .isEmployerContribution(structureReq.isEmployerContribution())
                        .isMandatory(structureReq.isMandatory())
                        .allowEmployeeOverride(structureReq.isAllowEmployeeOverride())
                        .isAllowedByTenant(true)
                        .minValue(structureReq.getMinValue())
                        .maxValue(structureReq.getMaxValue())
                        .effectiveFrom(effectiveFrom)
                        .effectiveTo(null)
                        .isActive(true)
                        .build();

                componentDefinitionRepo.save(definition);
            }
        }

        return toConfigResponse(savedConfig);
    }

    public TenantPayrollConfigResponse getActiveGlobalConfig(@NonNull Long tenantId) {
        log.info("Fetching active global config for tenant: {}", tenantId);
        TenantPayrollConfig config = tenantPayrollConfigRepo.findActiveByTenant(tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No active global config found for tenant: " + tenantId));
        return toConfigResponse(config);
    }

    // ============ Component Definitions ============

    @Transactional
    public SalaryComponentDefinitionResponse createComponentDefinition(@NonNull Long tenantId, @NonNull SalaryComponentDefinitionRequest request) {
        log.info("Creating component definition for tenant: {}, component: {}", tenantId, request.getComponentCode());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        TenantPayrollConfig activeConfig = tenantPayrollConfigRepo.findActiveByTenant(tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("No active payroll config found for tenant"));

        // Validate formula syntax
        if ("FORMULA".equals(request.getCalculationType()) && request.getFormulaExpression() != null) {
            validateFormula(request.getFormulaExpression());
        }

        // Check for duplicate component code
        if (componentDefinitionRepo.existsByTenantIdAndComponentCode(tenantId, request.getComponentCode().toUpperCase())) {
            throw new BusinessException("Component code already exists: " + request.getComponentCode());
        }

        SalaryComponentDefinition definition = SalaryComponentDefinition.builder()
                .tenant(tenant)
                .tenantPayrollConfigId(Objects.requireNonNull(activeConfig.getId()))
                .componentCode(request.getComponentCode().toUpperCase())
                .componentName(request.getComponentName())
                .componentType(request.getComponentType().toUpperCase())
                .calculationType(request.getCalculationType().toUpperCase())
                .defaultValue(request.getDefaultValue())
                .formulaExpression(request.getFormulaExpression())
                .evaluationOrder(request.getEvaluationOrder() != null ? request.getEvaluationOrder() : 5)
                .isLopApplicable(request.isLopApplicable())
                .isEmployerContribution(request.isEmployerContribution())
                .isMandatory(request.isMandatory())
                .allowEmployeeOverride(request.isAllowEmployeeOverride())
                .isAllowedByTenant(request.isAllowedByTenant())
                .minValue(request.getMinValue())
                .maxValue(request.getMaxValue())
                .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now())
                .effectiveTo(request.getEffectiveTo())
                .isActive(true)
                .build();

        SalaryComponentDefinition savedDef = componentDefinitionRepo.save(definition);
        return toComponentResponse(savedDef);
    }

    public List<SalaryComponentDefinitionResponse> getComponentsByTenant(@NonNull Long tenantId) {
        log.info("Fetching all components for tenant: {}", tenantId);
        List<SalaryComponentDefinition> components = componentDefinitionRepo.findActiveByTenantAndDate(tenantId, LocalDate.now());
        return components.stream()
                .map(this::toComponentResponse)
                .collect(Collectors.toList());
    }

    public List<SalaryComponentDefinitionResponse> getAllowedComponentsByTenant(@NonNull Long tenantId) {
        log.info("Fetching allowed components for tenant: {}", tenantId);
        List<SalaryComponentDefinition> components = componentDefinitionRepo.findAllowedByTenantAndDate(tenantId, LocalDate.now());
        return components.stream()
                .map(this::toComponentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void toggleComponentAllowed(@NonNull Long tenantId, @NonNull UUID componentId, boolean allowed) {
        log.info("Toggling component {} allowed status to: {}", componentId, allowed);
        SalaryComponentDefinition definition = componentDefinitionRepo.findById(componentId)
                .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + componentId));

        if (!definition.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Component does not belong to this tenant");
        }

        definition.setAllowedByTenant(allowed);
        componentDefinitionRepo.save(definition);
    }

    @Transactional
    public void deleteComponent(@NonNull Long tenantId, @NonNull UUID componentId) {
        log.info("Deleting component: {}", componentId);
        SalaryComponentDefinition definition = componentDefinitionRepo.findById(componentId)
                .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + componentId));

        if (!definition.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Component does not belong to this tenant");
        }

        if (definition.isMandatory()) {
            throw new BusinessException("Cannot delete mandatory component: " + definition.getComponentCode());
        }

        // Soft delete - just deactivate
        definition.setActive(false);
        definition.setEffectiveTo(LocalDate.now());
        componentDefinitionRepo.save(definition);
    }

    // ============ Employee Salary Profile Management ============

    @Transactional
    public EmployeeSalaryProfileResponse createEmployeeProfile(@NonNull Long tenantId, @NonNull Long employeeId,
                                                               @NonNull EmployeeSalaryProfileRequest request) {
        log.info("Creating salary profile for employee: {}", employeeId);

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Validate employee exists and belongs to tenant
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Verify employee belongs to tenant
        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Employee does not belong to this tenant");
        }

        // Get active profiles and version them
        List<EmployeeSalaryProfile> activeProfiles = employeeSalaryProfileRepo.findActiveByEmployeeId(employeeId);

        // Close active profiles
        LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();
        for (EmployeeSalaryProfile active : activeProfiles) {
            active.setActive(false);
            active.setEffectiveTo(effectiveFrom.minusDays(1));
            employeeSalaryProfileRepo.save(active);

            // Save history before closing
            saveProfileHistory(active, "VERSION_CLOSE");
        }

        // Get tenant config for defaults
        TenantPayrollConfig tenantConfig = tenantPayrollConfigRepo.findActiveByTenant(tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("No active payroll config found for tenant"));

        // Determine next version
        int nextVersion = employeeSalaryProfileRepo.findMaxVersionByEmployeeId(employeeId).orElse(0) + 1;

        EmployeeSalaryProfile profile = EmployeeSalaryProfile.builder()
                .tenant(tenant)
                .employee(employee)
                .version(nextVersion)
                .monthlyCtc(request.getMonthlyCtc())
                .currency(request.getCurrency() != null ? request.getCurrency() : tenantConfig.getDefaultCurrency())
                .taxRegime(request.getTaxRegime() != null ? request.getTaxRegime() : tenantConfig.getDefaultTaxRegime())
                .lopBasisOverride(request.getLopBasisOverride())
                .workingDaysOverride(request.getWorkingDaysOverride())
                .promotionReason(request.getPromotionReason())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(null)
                .isActive(true)
                .createdBy(request.getModifiedBy())
                .build();

        EmployeeSalaryProfile savedProfile = employeeSalaryProfileRepo.save(profile);

        // Process component overrides
        Map<String, EmployeeComponentOverrideDTO> overridesMap = new HashMap<>();
        if (request.getComponentOverrides() != null) {
            for (EmployeeComponentOverrideDTO override : request.getComponentOverrides()) {
                overridesMap.put(override.getComponentCode(), override);
            }
        }

        // Get all allowed components and create overrides
        List<SalaryComponentDefinition> allowedComponents = componentDefinitionRepo
                .findAllowedByTenantAndDate(tenantId, LocalDate.now());

        for (SalaryComponentDefinition definition : allowedComponents) {
            if (definition.isMandatory() || overridesMap.containsKey(definition.getComponentCode())) {
                EmployeeComponentOverrideDTO override = overridesMap.get(definition.getComponentCode());
                if (override != null) {
                    // Use provided override
                    createEmployeeComponent(savedProfile, definition, override);
                } else {
                    // Create default from global definition
                    EmployeeSalaryComponent defaultComponent = EmployeeSalaryComponent.builder()
                            .tenant(tenant)
                            .salaryProfile(savedProfile)
                            .componentCode(definition.getComponentCode())
                            .overrideType("VALUE")
                            .overrideValue(definition.getDefaultValue() != null ?
                                    definition.getDefaultValue() : BigDecimal.ZERO)
                            .isEnabled(true)
                            .createdBy(request.getModifiedBy())
                            .build();
                    employeeSalaryComponentRepo.save(defaultComponent);
                }
            }
        }

        // Save profile history
        saveProfileHistory(savedProfile, "NEW_VERSION_CREATED");

        return toEmployeeProfileResponse(savedProfile);
    }

    public EmployeeSalaryProfileResponse getActiveEmployeeProfile(@NonNull Long tenantId, @NonNull Long employeeId) {
        log.info("Fetching active salary profile for employee: {}", employeeId);

        // Verify employee belongs to tenant
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Employee does not belong to this tenant");
        }

        List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepo.findActiveByEmployeeId(employeeId);
        if (profiles.isEmpty()) {
            throw new ResourceNotFoundException("No active salary profile found for employee: " + employeeId);
        }
        return toEmployeeProfileResponse(profiles.get(0));
    }

    public List<EmployeeSalaryProfileResponse> getAllEmployeeProfiles(@NonNull Long tenantId, @NonNull Long employeeId) {
        log.info("Fetching all salary profiles for employee: {}", employeeId);

        // Verify employee belongs to tenant
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Employee does not belong to this tenant");
        }

        List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        return profiles.stream()
                .map(this::toEmployeeProfileResponse)
                .collect(Collectors.toList());
    }

    // ============ Promotion/Increment Handling ============

    @Transactional
    public EmployeeSalaryProfileResponse promoteEmployee(@NonNull Long tenantId, @NonNull Long employeeId,
                                                         @NonNull PromotionRequest request) {
        log.info("Processing promotion for employee: {}", employeeId);

        // Verify employee belongs to tenant
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Employee does not belong to this tenant");
        }

        // Get current active profile
        List<EmployeeSalaryProfile> activeProfiles = employeeSalaryProfileRepo.findActiveByEmployeeId(employeeId);
        if (activeProfiles.isEmpty()) {
            throw new ResourceNotFoundException("No active profile found for employee");
        }
        EmployeeSalaryProfile currentProfile = activeProfiles.get(0);

        // Close current profile
        currentProfile.setActive(false);
        currentProfile.setEffectiveTo(request.getEffectiveFrom().minusDays(1));
        EmployeeSalaryProfile savedCurrentProfile = employeeSalaryProfileRepo.save(currentProfile);
        saveProfileHistory(savedCurrentProfile, "PROMOTION_CLOSE");

        // Create new profile with updated CTC and components
        EmployeeSalaryProfileRequest newProfileRequest = EmployeeSalaryProfileRequest.builder()
                .employeeId(employeeId)
                .monthlyCtc(request.getNewCtc())
                .currency(currentProfile.getCurrency())
                .taxRegime(currentProfile.getTaxRegime())
                .lopBasisOverride(currentProfile.getLopBasisOverride())
                .workingDaysOverride(currentProfile.getWorkingDaysOverride())
                .promotionReason(request.getPromotionReason() != null ?
                        request.getPromotionReason() : "PROMOTION")
                .effectiveFrom(request.getEffectiveFrom())
                .componentOverrides(request.getComponentOverrides())
                .modifiedBy(request.getModifiedBy())
                .build();

        return createEmployeeProfile(tenantId, employeeId, newProfileRequest);
    }

    // ============ Employee Component Overrides ============

    @Transactional
    public EmployeeComponentOverrideDTO updateEmployeeComponent(@NonNull Long tenantId, @NonNull UUID profileId,
                                                                @NonNull EmployeeComponentOverrideDTO request) {
        log.info("Updating employee component: {} for profile: {}", request.getComponentCode(), profileId);

        EmployeeSalaryProfile profile = employeeSalaryProfileRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary profile not found: " + profileId));

        if (!profile.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Profile does not belong to this tenant");
        }

        // Validate that component exists in tenant's global definitions
        SalaryComponentDefinition definition = componentDefinitionRepo
                .findByTenantAndComponentCode(tenantId, request.getComponentCode())
                .orElseThrow(() -> new BusinessException("Component not found in tenant's global definitions: " + request.getComponentCode()));

        if (!definition.isAllowEmployeeOverride()) {
            throw new BusinessException("Employee override not allowed for component: " + request.getComponentCode());
        }

        // Validate override value
        if ("VALUE".equals(request.getOverrideType())) {
            if (definition.getMinValue() != null && request.getOverrideValue().compareTo(definition.getMinValue()) < 0) {
                throw new BusinessException("Override value below minimum: " + definition.getMinValue());
            }
            if (definition.getMaxValue() != null && request.getOverrideValue().compareTo(definition.getMaxValue()) > 0) {
                throw new BusinessException("Override value above maximum: " + definition.getMaxValue());
            }
        }

        if ("FORMULA".equals(request.getOverrideType()) && request.getOverrideFormula() != null) {
            validateFormula(request.getOverrideFormula());
        }

        // Check if override already exists
        Optional<EmployeeSalaryComponent> existing = employeeSalaryComponentRepo
                .findByProfileAndComponent(profileId, request.getComponentCode());

        EmployeeSalaryComponent component;
        if (existing.isPresent()) {
            component = Objects.requireNonNull(existing.get());
            component.setOverrideType(request.getOverrideType());
            component.setOverrideValue(request.getOverrideValue());
            component.setOverrideFormula(request.getOverrideFormula());
            component.setEnabled(request.isEnabled());
            component.setUpdatedBy(profile.getCreatedBy());
        } else {
            component = EmployeeSalaryComponent.builder()
                    .tenant(profile.getTenant())
                    .salaryProfile(profile)
                    .componentCode(request.getComponentCode())
                    .overrideType(request.getOverrideType() != null ? request.getOverrideType() : "VALUE")
                    .overrideValue(request.getOverrideValue())
                    .overrideFormula(request.getOverrideFormula())
                    .isEnabled(request.isEnabled())
                    .createdBy(profile.getCreatedBy())
                    .build();
        }

        EmployeeSalaryComponent savedComponent = employeeSalaryComponentRepo.save(component);

        // Update profile history
        saveProfileHistory(profile, "COMPONENT_UPDATED: " + request.getComponentCode());

        return toComponentOverrideDTO(savedComponent);
    }

    @Transactional
    public void syncEmployeeProfileFromGlobal(@NonNull Long tenantId, @NonNull UUID profileId) {
        log.info("Syncing employee profile {} with global definitions", profileId);
        EmployeeSalaryProfile profile = employeeSalaryProfileRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found: " + profileId));

        if (!profile.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Profile does not belong to this tenant");
        }

        // Get all active global components
        List<SalaryComponentDefinition> globalComponents = componentDefinitionRepo
                .findAllowedByTenantAndDate(tenantId, LocalDate.now());

        // Get existing employee overrides
        List<EmployeeSalaryComponent> existingOverrides = employeeSalaryComponentRepo
                .findBySalaryProfileId(profileId);
        Set<String> existingCodes = existingOverrides.stream()
                .map(EmployeeSalaryComponent::getComponentCode)
                .collect(Collectors.toSet());

        // Get tenant for creating new components
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        int addedCount = 0;
        int updatedCount = 0;

        // Create overrides for mandatory components that don't have them
        for (SalaryComponentDefinition definition : globalComponents) {
            if (definition.isMandatory() && !existingCodes.contains(definition.getComponentCode())) {
                EmployeeSalaryComponent override = EmployeeSalaryComponent.builder()
                        .tenant(tenant)
                        .salaryProfile(profile)
                        .componentCode(definition.getComponentCode())
                        .overrideType("VALUE")
                        .overrideValue(definition.getDefaultValue() != null ?
                                definition.getDefaultValue() : BigDecimal.ZERO)
                        .isEnabled(true)
                        .createdBy(profile.getCreatedBy())
                        .build();
                employeeSalaryComponentRepo.save(override);
                addedCount++;
            }
        }

        for (EmployeeSalaryComponent existing : existingOverrides) {
            if (existing.getOverrideValue() == null && existing.getOverrideFormula() == null) {
                // Find matching global definition
                Optional<SalaryComponentDefinition> matchedDef = globalComponents.stream()
                        .filter(def -> def.getComponentCode().equals(existing.getComponentCode()))
                        .findFirst();
                if (matchedDef.isPresent()) {
                    SalaryComponentDefinition def = matchedDef.get();
                    existing.setOverrideValue(def.getDefaultValue() != null ?
                            def.getDefaultValue() : BigDecimal.ZERO);
                    employeeSalaryComponentRepo.save(existing);
                    updatedCount++;
                }
            }
        }

        // Save profile history
        saveProfileHistory(profile, "SYNCED_WITH_GLOBAL: Added " + addedCount + ", Updated " + updatedCount);

        log.info("Profile sync completed. Added: {}, Updated: {}", addedCount, updatedCount);
    }

    // ============ Salary History ============

    public List<EmployeeSalaryProfileHistory> getEmployeeSalaryHistory(@NonNull Long tenantId, @NonNull Long employeeId) {
        log.info("Fetching salary history for employee: {}", employeeId);

        // Verify employee belongs to tenant
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Employee does not belong to this tenant");
        }

        return profileHistoryRepo.findByEmployeeIdOrderByChangedAtDesc(employeeId);
    }

    // ============ Bulk Sync Operation ============

    /**
     * Sync all employee profiles with global definitions
     * This ensures all employees have all mandatory components from the global configuration
     */
    @Transactional
    public void syncAllEmployeeProfiles(@NonNull Long tenantId) {
        log.info("Syncing all employee profiles with global definitions for tenant: {}", tenantId);

        // Verify tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Get all active employees for this tenant
        List<Employee> employees = employeeRepository.findActiveEmployeesByTenantId(tenantId);

        if (employees.isEmpty()) {
            log.info("No active employees found for tenant: {}", tenantId);
            return;
        }

        int syncedCount = 0;
        int failedCount = 0;

        for (Employee employee : employees) {
            try {
                // Get active profile for this employee
                List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepo.findActiveByEmployeeId(Objects.requireNonNull(employee.getId()));

                if (profiles.isEmpty()) {
                    log.warn("No active salary profile found for employee: {}", employee.getId());
                    continue;
                }

                // Sync each active profile
                for (EmployeeSalaryProfile profile : profiles) {
                    syncEmployeeProfileFromGlobal(tenantId, Objects.requireNonNull(profile.getId()));
                    syncedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to sync employee profile for employee: {}", employee.getId(), e);
                failedCount++;
            }
        }

        log.info("Sync completed for tenant: {}. Synced: {}, Failed: {}", tenantId, syncedCount, failedCount);

        if (failedCount > 0) {
            throw new BusinessException("Sync completed with " + failedCount + " failures. Check logs for details.");
        }
    }

    // ============ Helper Methods ============

    private void createEmployeeComponent(@NonNull EmployeeSalaryProfile profile,
                                         @NonNull SalaryComponentDefinition definition,
                                         @NonNull EmployeeComponentOverrideDTO override) {
        // Validate override
        if ("VALUE".equals(override.getOverrideType())) {
            if (definition.getMinValue() != null &&
                    override.getOverrideValue().compareTo(definition.getMinValue()) < 0) {
                throw new BusinessException("Value below minimum for " + definition.getComponentCode());
            }
            if (definition.getMaxValue() != null &&
                    override.getOverrideValue().compareTo(definition.getMaxValue()) > 0) {
                throw new BusinessException("Value above maximum for " + definition.getComponentCode());
            }
        }

        if ("FORMULA".equals(override.getOverrideType()) && override.getOverrideFormula() != null) {
            validateFormula(override.getOverrideFormula());
        }

        EmployeeSalaryComponent component = EmployeeSalaryComponent.builder()
                .tenant(profile.getTenant())
                .salaryProfile(profile)
                .componentCode(definition.getComponentCode())
                .overrideType(override.getOverrideType() != null ? override.getOverrideType() : "VALUE")
                .overrideValue(override.getOverrideValue())
                .overrideFormula(override.getOverrideFormula())
                .isEnabled(override.isEnabled())
                .createdBy(profile.getCreatedBy())
                .build();

        employeeSalaryComponentRepo.save(component);
    }

    private void saveProfileHistory(@NonNull EmployeeSalaryProfile profile, String reason) {
        try {
            // Get all components for this profile
            List<EmployeeSalaryComponent> components = employeeSalaryComponentRepo
                    .findBySalaryProfileId(profile.getId());

            String componentSnapshot = objectMapper.writeValueAsString(components);

            EmployeeSalaryProfileHistory history = EmployeeSalaryProfileHistory.builder()
                    .profileId(profile.getId())
                    .employeeId(profile.getEmployee().getId())
                    .version(profile.getVersion())
                    .monthlyCtc(profile.getMonthlyCtc())
                    .effectiveFrom(profile.getEffectiveFrom())
                    .effectiveTo(profile.getEffectiveTo())
                    .changeReason(reason)
                    .changedBy(profile.getUpdatedBy() != null ?
                            profile.getUpdatedBy() : profile.getCreatedBy())
                    .changedAt(LocalDateTime.now())
                    .componentSnapshot(componentSnapshot)
                    .build();

            profileHistoryRepo.save(history);
        } catch (Exception e) {
            log.error("Failed to save profile history", e);
        }
    }

    private void validateFormula(String formula) {
        try {
            Map<String, Object> testVariables = new HashMap<>();
            testVariables.put("CTC", 100000.0);
            testVariables.put("BASIC", 50000.0);
            testVariables.put("HRA", 20000.0);
            testVariables.put("EPF_ER_RATE", 0.12);
            testVariables.put("EPS_ER_CAP", 1250.0);
            spelEngine.evaluate(formula, testVariables);
        } catch (Exception e) {
            throw new BusinessException("Invalid formula syntax: " + e.getMessage());
        }
    }

    /**
     * Validate formula syntax (public method for API)
     */
    public boolean validateFormulaSyntax(String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return true;
        }
        try {
            Map<String, Object> testVariables = new HashMap<>();
            testVariables.put("CTC", 100000.0);
            testVariables.put("BASIC", 50000.0);
            testVariables.put("HRA", 20000.0);
            testVariables.put("EPF_ER_RATE", 0.12);
            testVariables.put("EPS_ER_CAP", 1250.0);
            testVariables.put("EDLI_CEILING", 15000.0);
            testVariables.put("ESI_EE_RATE", 0.0075);
            testVariables.put("WAGES_BASE", 50000.0);
            testVariables.put("GROSS", 100000.0);
            spelEngine.evaluate(formula, testVariables);
            return true;
        } catch (Exception e) {
            log.debug("Formula validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ============ Response Mappers ============

    private TenantPayrollConfigResponse toConfigResponse(@NonNull TenantPayrollConfig config) {
        // Get salary structures for this config
        List<SalaryComponentDefinition> components = componentDefinitionRepo
                .findByTenantPayrollConfigId(config.getId());

        List<TenantPayrollConfigResponse.SalaryStructureResponse> structureResponses = components.stream()
                .map(comp -> TenantPayrollConfigResponse.SalaryStructureResponse.builder()
                        .id(comp.getId())
                        .componentCode(comp.getComponentCode())
                        .componentName(comp.getComponentName())
                        .componentType(comp.getComponentType())
                        .calculationType(comp.getCalculationType())
                        .value(comp.getDefaultValue())
                        .evaluationOrder(comp.getEvaluationOrder())
                        .isPartOfPfWages(true)
                        .isPartOfEsiWages(true)
                        .isTaxable(true)
                        .isLopApplicable(comp.isLopApplicable())
                        .isEmployerContribution(comp.isEmployerContribution())
                        .isMandatory(comp.isMandatory())
                        .allowEmployeeOverride(comp.isAllowEmployeeOverride())
                        .minValue(comp.getMinValue())
                        .maxValue(comp.getMaxValue())
                        .formulaExpression(comp.getFormulaExpression())
                        .effectiveFrom(comp.getEffectiveFrom())
                        .effectiveTo(comp.getEffectiveTo())
                        .isActive(comp.isActive())
                        .build())
                .collect(Collectors.toList());

        return TenantPayrollConfigResponse.builder()
                .id(config.getId())
                .tenantId(config.getTenant().getId())
                .lopBasis(config.getLopBasis())
                .workingDaysPerMonth(config.getWorkingDaysPerMonth())
                .enablePfCapping(config.isEnablePfCapping())
                .enableEsi(config.isEnableEsi())
                .enablePt(config.isEnablePt())
                .enforceNewLabourCodes(config.isEnforceNewLabourCodes())
                .enableOvertime(config.isEnableOvertime())
                .overtimeRatePerHour(config.getOvertimeRatePerHour())
                .defaultCurrency(config.getDefaultCurrency())
                .defaultTaxRegime(config.getDefaultTaxRegime())
                .effectiveFrom(config.getEffectiveFrom())
                .effectiveTo(config.getEffectiveTo())
                .isActive(config.isActive())
                .salaryStructures(structureResponses)
                .build();
    }

    private SalaryComponentDefinitionResponse toComponentResponse(@NonNull SalaryComponentDefinition definition) {
        return SalaryComponentDefinitionResponse.builder()
                .id(definition.getId())
                .componentCode(definition.getComponentCode())
                .componentName(definition.getComponentName())
                .componentType(definition.getComponentType())
                .calculationType(definition.getCalculationType())
                .defaultValue(definition.getDefaultValue())
                .formulaExpression(definition.getFormulaExpression())
                .evaluationOrder(definition.getEvaluationOrder())
                .isLopApplicable(definition.isLopApplicable())
                .isEmployerContribution(definition.isEmployerContribution())
                .isMandatory(definition.isMandatory())
                .allowEmployeeOverride(definition.isAllowEmployeeOverride())
                .isAllowedByTenant(definition.isAllowedByTenant())
                .minValue(definition.getMinValue())
                .maxValue(definition.getMaxValue())
                .effectiveFrom(definition.getEffectiveFrom())
                .effectiveTo(definition.getEffectiveTo())
                .isActive(definition.isActive())
                .build();
    }

    private EmployeeSalaryProfileResponse toEmployeeProfileResponse(@NonNull EmployeeSalaryProfile profile) {
        List<EmployeeSalaryComponent> overrides = employeeSalaryComponentRepo
                .findBySalaryProfileId(profile.getId());

        Employee employee = profile.getEmployee();

        return EmployeeSalaryProfileResponse.builder()
                .id(profile.getId())
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .employeeName(employee.getFullName())
                .version(profile.getVersion())
                .monthlyCtc(profile.getMonthlyCtc())
                .currency(profile.getCurrency())
                .taxRegime(profile.getTaxRegime())
                .lopBasisOverride(profile.getLopBasisOverride())
                .workingDaysOverride(profile.getWorkingDaysOverride())
                .promotionReason(profile.getPromotionReason())
                .effectiveFrom(profile.getEffectiveFrom())
                .effectiveTo(profile.getEffectiveTo())
                .isActive(profile.isActive())
                .componentOverrides(overrides.stream()
                        .map(this::toComponentOverrideDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private EmployeeComponentOverrideDTO toComponentOverrideDTO(@NonNull EmployeeSalaryComponent component) {
        return EmployeeComponentOverrideDTO.builder()
                .componentCode(component.getComponentCode())
                .overrideType(component.getOverrideType())
                .overrideValue(component.getOverrideValue())
                .overrideFormula(component.getOverrideFormula())
                .isEnabled(component.isEnabled())
                .build();
    }

    // ============ Additional Public Methods ============

    /**
     * Get all employee profiles for a tenant
     */
    public List<EmployeeSalaryProfileResponse> getAllEmployeeProfilesForTenant(@NonNull Long tenantId) {
        log.info("Fetching all employee profiles for tenant: {}", tenantId);

        // Verify tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<Employee> employees = employeeRepository.findActiveEmployeesByTenantId(tenantId);
        List<EmployeeSalaryProfileResponse> responses = new ArrayList<>();

        for (Employee employee : employees) {
            List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepo.findActiveByEmployeeId(Objects.requireNonNull(employee.getId()));
            for (EmployeeSalaryProfile profile : profiles) {
                responses.add(toEmployeeProfileResponse(profile));
            }
        }

        return responses;
    }
}