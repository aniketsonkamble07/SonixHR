package com.sonixhr.service.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.exceptions.TechnicalException;
import com.sonixhr.repository.payroll.PayrunConfigRepository;
import com.sonixhr.repository.payroll.TaxRegimeSlabConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    private static final String ENGINE_VERSION = "1.0.0";
    private static final String FORMULA_ENGINE_VERSION = "1.0.0";
    private static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final PayrunConfigRepository payrunConfigRepo;
    private final TaxRegimeSlabConfigRepository taxSlabConfigRepo;
    private final ObjectMapper objectMapper;

    public void createPayrunSnapshot(
            Payrun payrun,
            List<StatutoryRateConfig> statutoryRates,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> salaryStructure,
            List<StateProfessionalTaxConfig> ptSlabs) {
        try {
            String financialYear = resolveFinancialYear(payrun.getMonth(), payrun.getYear());
            List<TaxRegimeSlabConfig> taxSlabConfigs = taxSlabConfigRepo.findByFinancialYear(financialYear);

            PayrunConfig snapshot = PayrunConfig.builder()
                    .payrunId(payrun.getId())
                    .statutoryRatesJson(objectMapper.writeValueAsString(statutoryRates))
                    .tenantSettingsJson(objectMapper.writeValueAsString(tenantConfig))
                    .tenantStructureJson(objectMapper.writeValueAsString(salaryStructure))
                    .ptSlabsJson(objectMapper.writeValueAsString(ptSlabs))
                    .taxSlabConfigJson(objectMapper.writeValueAsString(taxSlabConfigs))
                    .engineVersion(ENGINE_VERSION)
                    .formulaEngineVersion(FORMULA_ENGINE_VERSION)
                    .snapshotSchemaVersion(SNAPSHOT_SCHEMA_VERSION)
                    .snapshotCreatedAt(LocalDateTime.now())
                    .build();
            payrunConfigRepo.save(snapshot);
        } catch (Exception e) {
            throw new TechnicalException("TECH_PAYRUN_SNAPSHOT", "Payrun processing failed",
                    "Failed to serialize payrun configurations for snapshot", e);
        }
    }

    private String resolveFinancialYear(int month, int year) {
        // Indian FY: April(4) to March(3)
        int fyStartYear = (month >= 4) ? year : year - 1;
        int fyEndYearShort = (fyStartYear + 1) % 100;
        return fyStartYear + "-" + String.format("%02d", fyEndYearShort);
    }
}
