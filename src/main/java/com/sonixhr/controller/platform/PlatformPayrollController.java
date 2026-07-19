package com.sonixhr.controller.platform;

import com.sonixhr.dto.payroll.TaxRegimeSlabConfigDTO;
import com.sonixhr.dto.payroll.StatutoryRateConfigDTO;
import com.sonixhr.dto.payroll.StateProfessionalTaxConfigDTO;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import com.sonixhr.service.payroll.TaxSlabConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/platform/payroll")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformPayrollController {

    private final StatutoryRateConfigRepository statutoryRateConfigRepository;
    private final StateProfessionalTaxConfigRepository statePtConfigRepository;
    private final TaxSlabConfigService taxSlabConfigService;

    // --- Statutory Rate Configs ---

    @GetMapping("/statutory-rates")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<List<StatutoryRateConfigDTO>> getAllStatutoryRates() {
        log.info("REST request to list all platform statutory rates");
        List<StatutoryRateConfigDTO> list = statutoryRateConfigRepository.findAllByIsDeletedFalse().stream()
                .map(this::convertToDTO)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/statutory-rates")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StatutoryRateConfigDTO> createStatutoryRate(@Valid @RequestBody StatutoryRateConfigDTO dto) {
        log.info("REST request to create statutory rate config for: {}", dto.getComponentCode());
        StatutoryRateConfig entity = convertToEntity(dto);
        StatutoryRateConfig saved = statutoryRateConfigRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(saved));
    }

    @PutMapping("/statutory-rates/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StatutoryRateConfigDTO> updateStatutoryRate(
            @PathVariable UUID id,
            @Valid @RequestBody StatutoryRateConfigDTO dto) {
        log.info("REST request to update statutory rate config ID: {}", id);
        StatutoryRateConfig existing = statutoryRateConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statutory rate config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("Statutory rate config not found");
        }

        existing.setComponentCode(dto.getComponentCode());
        existing.setRate(dto.getRate());
        existing.setWageBase(dto.getWageBase());
        existing.setCeilingAmount(dto.getCeilingAmount());
        existing.setCapAmount(dto.getCapAmount());
        existing.setEffectiveFrom(dto.getEffectiveFrom());
        existing.setEffectiveTo(dto.getEffectiveTo());

        StatutoryRateConfig saved = statutoryRateConfigRepository.save(existing);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/statutory-rates/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<Void> deleteStatutoryRate(@PathVariable UUID id) {
        log.info("REST request to delete statutory rate config ID: {}", id);
        StatutoryRateConfig existing = statutoryRateConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statutory rate config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("Statutory rate config not found");
        }
        existing.setDeleted(true);
        statutoryRateConfigRepository.save(existing);
        return ResponseEntity.noContent().build();
    }

    // --- State Professional Tax Configs ---

    @GetMapping("/pt-configs")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<List<StateProfessionalTaxConfigDTO>> getAllPtConfigs() {
        log.info("REST request to list all platform state PT configs");
        List<StateProfessionalTaxConfigDTO> list = statePtConfigRepository.findAllByIsDeletedFalse().stream()
                .map(this::convertToDTO)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/pt-configs")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StateProfessionalTaxConfigDTO> createPtConfig(@Valid @RequestBody StateProfessionalTaxConfigDTO dto) {
        log.info("REST request to create state PT config for: {}", dto.getStateCode());
        StateProfessionalTaxConfig entity = convertToEntity(dto);
        StateProfessionalTaxConfig saved = statePtConfigRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(saved));
    }

    @PutMapping("/pt-configs/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StateProfessionalTaxConfigDTO> updatePtConfig(
            @PathVariable UUID id,
            @Valid @RequestBody StateProfessionalTaxConfigDTO dto) {
        log.info("REST request to update state PT config ID: {}", id);
        StateProfessionalTaxConfig existing = statePtConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("State PT config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("State PT config not found");
        }

        existing.setStateCode(dto.getStateCode());
        existing.setSalaryRangeMin(dto.getSalaryRangeMin());
        existing.setSalaryRangeMax(dto.getSalaryRangeMax());
        existing.setApplicableMonth(dto.getApplicableMonth());
        existing.setAmount(dto.getAmount());
        existing.setEffectiveFrom(dto.getEffectiveFrom());
        existing.setEffectiveTo(dto.getEffectiveTo());

        StateProfessionalTaxConfig saved = statePtConfigRepository.save(existing);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/pt-configs/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<Void> deletePtConfig(@PathVariable UUID id) {
        log.info("REST request to delete state PT config ID: {}", id);
        StateProfessionalTaxConfig existing = statePtConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("State PT config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("State PT config not found");
        }
        existing.setDeleted(true);
        statePtConfigRepository.save(existing);
        return ResponseEntity.noContent().build();
    }

    // --- Tax Regime Slab Configs ---

    @GetMapping("/tax-slabs")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<List<TaxRegimeSlabConfigDTO>> getAllTaxSlabs() {
        log.info("REST request to list all platform tax regime slab configs");
        return ResponseEntity.ok(taxSlabConfigService.getAllTaxSlabs());
    }

    @PostMapping("/tax-slabs")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<TaxRegimeSlabConfigDTO> createTaxSlab(@Valid @RequestBody TaxRegimeSlabConfigDTO config) {
        log.info("REST request to create tax regime slab config for FY: {} - Regime: {}", config.getFinancialYear(), config.getRegime());
        TaxRegimeSlabConfigDTO saved = taxSlabConfigService.createTaxSlab(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/tax-slabs/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<TaxRegimeSlabConfigDTO> updateTaxSlab(
            @PathVariable UUID id,
            @Valid @RequestBody TaxRegimeSlabConfigDTO config) {
        log.info("REST request to update tax regime slab config ID: {}", id);
        TaxRegimeSlabConfigDTO saved = taxSlabConfigService.updateTaxSlab(id, config);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/tax-slabs/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<Void> deleteTaxSlab(@PathVariable UUID id) {
        log.info("REST request to delete tax regime slab config ID: {}", id);
        taxSlabConfigService.deleteTaxSlab(id);
        return ResponseEntity.noContent().build();
    }

    // --- Conversions ---

    private StatutoryRateConfigDTO convertToDTO(StatutoryRateConfig config) {
        if (config == null) return null;
        return StatutoryRateConfigDTO.builder()
                .id(config.getId())
                .componentCode(config.getComponentCode())
                .rate(config.getRate())
                .wageBase(config.getWageBase())
                .ceilingAmount(config.getCeilingAmount())
                .capAmount(config.getCapAmount())
                .effectiveFrom(config.getEffectiveFrom())
                .effectiveTo(config.getEffectiveTo())
                .build();
    }

    private StatutoryRateConfig convertToEntity(StatutoryRateConfigDTO dto) {
        if (dto == null) return null;
        return StatutoryRateConfig.builder()
                .id(dto.getId())
                .componentCode(dto.getComponentCode())
                .rate(dto.getRate())
                .wageBase(dto.getWageBase())
                .ceilingAmount(dto.getCeilingAmount())
                .capAmount(dto.getCapAmount())
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveTo(dto.getEffectiveTo())
                .build();
    }

    private StateProfessionalTaxConfigDTO convertToDTO(StateProfessionalTaxConfig config) {
        if (config == null) return null;
        return StateProfessionalTaxConfigDTO.builder()
                .id(config.getId())
                .stateCode(config.getStateCode())
                .salaryRangeMin(config.getSalaryRangeMin())
                .salaryRangeMax(config.getSalaryRangeMax())
                .applicableMonth(config.getApplicableMonth())
                .amount(config.getAmount())
                .effectiveFrom(config.getEffectiveFrom())
                .effectiveTo(config.getEffectiveTo())
                .build();
    }

    private StateProfessionalTaxConfig convertToEntity(StateProfessionalTaxConfigDTO dto) {
        if (dto == null) return null;
        return StateProfessionalTaxConfig.builder()
                .id(dto.getId())
                .stateCode(dto.getStateCode())
                .salaryRangeMin(dto.getSalaryRangeMin())
                .salaryRangeMax(dto.getSalaryRangeMax())
                .applicableMonth(dto.getApplicableMonth())
                .amount(dto.getAmount())
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveTo(dto.getEffectiveTo())
                .build();
    }
}
