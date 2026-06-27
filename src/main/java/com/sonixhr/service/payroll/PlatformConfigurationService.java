package com.sonixhr.service.payroll;

import com.sonixhr.dto.payroll.PlatformProfessionalTaxSlabDTO;
import com.sonixhr.dto.payroll.PlatformStatutoryRateDTO;
import com.sonixhr.enums.IndianState;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PlatformConfigurationService {

    private final StatutoryRateConfigRepository statutoryRateConfigRepo;
    private final StateProfessionalTaxConfigRepository statePtConfigRepo;

    public List<PlatformStatutoryRateDTO> getStatutoryRates(LocalDate date) {
        log.info("Fetching platform statutory rates active on: {}", date);
        List<StatutoryRateConfig> rates = statutoryRateConfigRepo.findActiveByDate(date);
        return rates.stream()
                .map(this::toStatutoryRateDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlatformStatutoryRateDTO createStatutoryRate(PlatformStatutoryRateDTO dto) {
        log.info("Creating statutory rate for: {}", dto.getComponentCode());
        StatutoryRateConfig entity = StatutoryRateConfig.builder()
                .componentCode(dto.getComponentCode().toUpperCase())
                .rate(dto.getRate())
                .wageBase(dto.getApplicableTo() != null ? dto.getApplicableTo() : "WAGES_BASE")
                .ceilingAmount(dto.getCeilingAmount())
                .capAmount(dto.getCapAmount())
                .effectiveFrom(dto.getEffectiveFrom() != null ? dto.getEffectiveFrom() : LocalDate.now())
                .effectiveTo(dto.getEffectiveTo())
                .build();

        entity = statutoryRateConfigRepo.save(entity);
        return toStatutoryRateDTO(entity);
    }

    @Transactional
    public PlatformStatutoryRateDTO updateStatutoryRate(UUID id, PlatformStatutoryRateDTO dto) {
        log.info("Updating statutory rate id: {}", id);
        StatutoryRateConfig entity = statutoryRateConfigRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statutory rate config not found with id: " + id));

        entity.setRate(dto.getRate());
        entity.setCeilingAmount(dto.getCeilingAmount());
        entity.setCapAmount(dto.getCapAmount());
        if (dto.getEffectiveFrom() != null) {
            entity.setEffectiveFrom(dto.getEffectiveFrom());
        }
        entity.setEffectiveTo(dto.getEffectiveTo());
        if (dto.getApplicableTo() != null) {
            entity.setWageBase(dto.getApplicableTo());
        }

        entity = statutoryRateConfigRepo.save(entity);
        return toStatutoryRateDTO(entity);
    }

    public List<PlatformProfessionalTaxSlabDTO> getPTSlabs(String stateCode, Integer month) {
        log.info("Fetching PT slabs for state: {}, month: {}", stateCode, month);
        IndianState targetState = stateCode != null ? IndianState.fromCode(stateCode) : null;
        List<StateProfessionalTaxConfig> configs = statePtConfigRepo.findAll();
        return configs.stream()
                .filter(c -> stateCode == null || (targetState != null && c.getStateCode() == targetState))
                .filter(c -> month == null || c.getApplicableMonth() == null || c.getApplicableMonth().equals(month))
                .map(this::toPTSlabDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlatformProfessionalTaxSlabDTO createPTSlab(PlatformProfessionalTaxSlabDTO dto) {
        log.info("Creating PT Slab for state: {}", dto.getStateCode());
        StateProfessionalTaxConfig entity = StateProfessionalTaxConfig.builder()
                .stateCode(dto.getStateCode())
                .salaryRangeMin(dto.getSalaryRangeMin())
                .salaryRangeMax(dto.getSalaryRangeMax())
                .applicableMonth(dto.getApplicableMonth())
                .amount(dto.getTaxAmount())
                .effectiveFrom(dto.getEffectiveFrom() != null ? dto.getEffectiveFrom() : LocalDate.now())
                .effectiveTo(dto.getEffectiveTo())
                .build();

        entity = statePtConfigRepo.save(entity);
        return toPTSlabDTO(entity);
    }

    private PlatformStatutoryRateDTO toStatutoryRateDTO(StatutoryRateConfig entity) {
        boolean active = entity.getEffectiveTo() == null || !entity.getEffectiveTo().isBefore(LocalDate.now());
        return PlatformStatutoryRateDTO.builder()
                .id(entity.getId())
                .componentCode(entity.getComponentCode())
                .componentName(getStatutoryComponentName(entity.getComponentCode()))
                .rate(entity.getRate())
                .ceilingAmount(entity.getCeilingAmount())
                .capAmount(entity.getCapAmount())
                .applicableTo(entity.getWageBase())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .isActive(active)
                .description("Platform configuration for " + entity.getComponentCode())
                .build();
    }

    private PlatformProfessionalTaxSlabDTO toPTSlabDTO(StateProfessionalTaxConfig entity) {
        boolean active = entity.getEffectiveTo() == null || !entity.getEffectiveTo().isBefore(LocalDate.now());
        return PlatformProfessionalTaxSlabDTO.builder()
                .id(entity.getId())
                .stateCode(entity.getStateCode())
                .stateName(entity.getStateCode() != null ? entity.getStateCode().getDisplayName() : "")
                .salaryRangeMin(entity.getSalaryRangeMin())
                .salaryRangeMax(entity.getSalaryRangeMax())
                .taxAmount(entity.getAmount())
                .applicableMonth(entity.getApplicableMonth())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .isActive(active)
                .build();
    }

    private String getStatutoryComponentName(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "EPF_EE": return "EPF Employee Contribution";
            case "EPF_ER": return "EPF Employer Contribution";
            case "EPS_ER": return "EPS Pension Share";
            case "EDLI": return "EDLI Insurance Premium";
            case "ESI_EE": return "ESI Employee Contribution";
            case "ESI_ER": return "ESI Employer Contribution";
            default: return componentCode;
        }
    }
}
