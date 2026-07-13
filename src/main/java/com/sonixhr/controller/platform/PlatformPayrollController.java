package com.sonixhr.controller.platform;

import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
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

    // --- Statutory Rate Configs ---

    @GetMapping("/statutory-rates")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<List<StatutoryRateConfig>> getAllStatutoryRates() {
        log.info("REST request to list all platform statutory rates");
        return ResponseEntity.ok(statutoryRateConfigRepository.findAllByIsDeletedFalse());
    }

    @PostMapping("/statutory-rates")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StatutoryRateConfig> createStatutoryRate(@Valid @RequestBody StatutoryRateConfig config) {
        log.info("REST request to create statutory rate config for: {}", config.getComponentCode());
        StatutoryRateConfig saved = statutoryRateConfigRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/statutory-rates/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StatutoryRateConfig> updateStatutoryRate(
            @PathVariable UUID id,
            @RequestBody StatutoryRateConfig config) {
        log.info("REST request to update statutory rate config ID: {}", id);
        StatutoryRateConfig existing = statutoryRateConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statutory rate config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("Statutory rate config not found");
        }

        existing.setComponentCode(config.getComponentCode());
        existing.setRate(config.getRate());
        existing.setWageBase(config.getWageBase());
        existing.setCeilingAmount(config.getCeilingAmount());
        existing.setCapAmount(config.getCapAmount());
        existing.setEffectiveFrom(config.getEffectiveFrom());
        existing.setEffectiveTo(config.getEffectiveTo());

        StatutoryRateConfig saved = statutoryRateConfigRepository.save(existing);
        return ResponseEntity.ok(saved);
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
    public ResponseEntity<List<StateProfessionalTaxConfig>> getAllPtConfigs() {
        log.info("REST request to list all platform state PT configs");
        return ResponseEntity.ok(statePtConfigRepository.findAllByIsDeletedFalse());
    }

    @PostMapping("/pt-configs")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StateProfessionalTaxConfig> createPtConfig(@Valid @RequestBody StateProfessionalTaxConfig config) {
        log.info("REST request to create state PT config for: {}", config.getStateCode());
        StateProfessionalTaxConfig saved = statePtConfigRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/pt-configs/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SYSTEM_SETTINGS')")
    public ResponseEntity<StateProfessionalTaxConfig> updatePtConfig(
            @PathVariable UUID id,
            @RequestBody StateProfessionalTaxConfig config) {
        log.info("REST request to update state PT config ID: {}", id);
        StateProfessionalTaxConfig existing = statePtConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("State PT config not found"));
        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("State PT config not found");
        }

        existing.setStateCode(config.getStateCode());
        existing.setSalaryRangeMin(config.getSalaryRangeMin());
        existing.setSalaryRangeMax(config.getSalaryRangeMax());
        existing.setApplicableMonth(config.getApplicableMonth());
        existing.setAmount(config.getAmount());
        existing.setEffectiveFrom(config.getEffectiveFrom());
        existing.setEffectiveTo(config.getEffectiveTo());

        StateProfessionalTaxConfig saved = statePtConfigRepository.save(existing);
        return ResponseEntity.ok(saved);
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
}
