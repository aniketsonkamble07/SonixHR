package com.sonixhr.service.payroll;

import com.sonixhr.dto.payroll.TenantPayrollConfigRequest;
import com.sonixhr.dto.payroll.TenantPayrollConfigResponse;
import com.sonixhr.entity.payroll.LopBasis;
import com.sonixhr.entity.payroll.TenantPayrollConfig;
import com.sonixhr.entity.payroll.TenantSalaryStructure;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.payroll.TenantPayrollConfigRepository;
import com.sonixhr.repository.payroll.TenantSalaryStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class TenantPayrollConfigService {

    private final TenantRepository tenantRepository;
    private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
    private final TenantSalaryStructureRepository tenantSalaryStructureRepo;

    @Transactional
    public TenantPayrollConfigResponse updateConfig(Long tenantId, TenantPayrollConfigRequest request) {
        log.info("Updating global payroll configuration for tenant: {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();

        // Validation
        if (LopBasis.WORKING_DAYS.equals(request.getLopBasis()) && request.getWorkingDaysPerMonth() == null) {
            throw new BusinessException("Working days per month is required when LOP basis is set to WORKING_DAYS");
        }

        if (request.getSalaryStructures() == null || request.getSalaryStructures().isEmpty()) {
            throw new BusinessException("Salary structure components list cannot be empty");
        }

        // 1. Version existing active TenantPayrollConfig
        Optional<TenantPayrollConfig> activeConfigOpt = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, effectiveFrom);
        if (activeConfigOpt.isPresent()) {
            TenantPayrollConfig activeConfig = activeConfigOpt.get();
            if (activeConfig.getEffectiveFrom().isAfter(effectiveFrom) || activeConfig.getEffectiveFrom().isEqual(effectiveFrom)) {
                throw new BusinessException("New effective date must be after the active configuration's start date (" + activeConfig.getEffectiveFrom() + ")");
            }
            activeConfig.setEffectiveTo(effectiveFrom.minusDays(1));
            tenantPayrollConfigRepo.save(activeConfig);
        }

        // 2. Version existing active TenantSalaryStructure records
        List<TenantSalaryStructure> activeStructures = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId, effectiveFrom);
        for (TenantSalaryStructure structure : activeStructures) {
            structure.setEffectiveTo(effectiveFrom.minusDays(1));
            tenantSalaryStructureRepo.save(structure);
        }

        // 3. Save new TenantPayrollConfig
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
                .defaultTaxRegime(request.getDefaultTaxRegime() != null ? request.getDefaultTaxRegime() : "NEW_REGIME")
                .effectiveFrom(effectiveFrom)
                .effectiveTo(null)
                .build();
        newConfig = tenantPayrollConfigRepo.save(newConfig);

        // 4. Save new TenantSalaryStructure records
        for (TenantPayrollConfigRequest.SalaryStructureRequest structureReq : request.getSalaryStructures()) {
            TenantSalaryStructure structure = TenantSalaryStructure.builder()
                    .tenant(tenant)
                    .tenantPayrollConfigId(newConfig.getId())
                    .componentCode(structureReq.getComponentCode().toUpperCase())
                    .calculationType(structureReq.getCalculationType().toUpperCase())
                    .value(structureReq.getValue())
                    .evaluationOrder(structureReq.getEvaluationOrder())
                    .isPartOfPfWages(structureReq.isPartOfPfWages())
                    .isPartOfEsiWages(structureReq.isPartOfEsiWages())
                    .isTaxable(structureReq.isTaxable())
                    .effectiveFrom(effectiveFrom)
                    .effectiveTo(null)
                    .build();
            tenantSalaryStructureRepo.save(structure);
        }

        return getActiveConfigResponse(tenantId, effectiveFrom);
    }

    public TenantPayrollConfigResponse getActiveConfig(Long tenantId) {
        LocalDate today = LocalDate.now();
        return getActiveConfigResponse(tenantId, today);
    }

    private TenantPayrollConfigResponse getActiveConfigResponse(Long tenantId, LocalDate date) {
        // Retrieve active configuration
        TenantPayrollConfig config = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, date)
                .orElseThrow(() -> new ResourceNotFoundException("No active global payroll configuration found for this tenant."));

        // Retrieve active salary structures for this tenant on the config's start date
        List<TenantSalaryStructure> structures = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId, config.getEffectiveFrom());

        List<TenantPayrollConfigResponse.SalaryStructureResponse> structuresResponses = structures.stream()
                .map(s -> TenantPayrollConfigResponse.SalaryStructureResponse.builder()
                        .id(s.getId())
                        .componentCode(s.getComponentCode())
                        .calculationType(s.getCalculationType())
                        .value(s.getValue())
                        .evaluationOrder(s.getEvaluationOrder())
                        .isPartOfPfWages(s.isPartOfPfWages())
                        .isPartOfEsiWages(s.isPartOfEsiWages())
                        .isTaxable(s.isTaxable())
                        .effectiveFrom(s.getEffectiveFrom())
                        .effectiveTo(s.getEffectiveTo())
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
                .effectiveFrom(config.getEffectiveFrom())
                .effectiveTo(config.getEffectiveTo())
                .salaryStructures(structuresResponses)
                .build();
    }
}
