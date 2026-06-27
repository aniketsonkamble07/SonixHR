package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.SubscriptionPlanDTO;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/platform/subscription-plans")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformSubscriptionPlanController {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS')")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        log.info("REST request to list all subscription plans");
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAll();
        List<SubscriptionPlanDTO> dtos = plans.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS')")
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(@PathVariable Long id) {
        log.info("REST request to get subscription plan for ID: {}", id);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));
        return ResponseEntity.ok(convertToDTO(plan));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@RequestBody @Valid SubscriptionPlanDTO dto) {
        log.info("REST request to create subscription plan: {}", dto.getCode());
        
        if (subscriptionPlanRepository.findByCodeIgnoreCase(dto.getCode()).isPresent()) {
            throw new IllegalArgumentException("Plan with code '" + dto.getCode() + "' already exists");
        }

        if (dto.isTrial()) {
            // Ensure only one trial plan is active
            subscriptionPlanRepository.findFirstByIsTrialTrueAndIsActiveTrue().ifPresent(existingTrial -> {
                existingTrial.setTrial(false);
                subscriptionPlanRepository.save(existingTrial);
            });
        }

        SubscriptionPlan plan = convertToEntity(dto);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(
            @PathVariable Long id,
            @RequestBody @Valid SubscriptionPlanDTO dto) {
        log.info("REST request to update subscription plan ID: {}", id);
        
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (dto.isTrial() && !plan.isTrial()) {
            // Ensure only one trial plan is active
            subscriptionPlanRepository.findFirstByIsTrialTrueAndIsActiveTrue().ifPresent(existingTrial -> {
                existingTrial.setTrial(false);
                subscriptionPlanRepository.save(existingTrial);
            });
        }

        plan.setName(dto.getName());
        plan.setMonthlyPrice(dto.getMonthlyPrice());
        plan.setMaxEmployees(dto.getMaxEmployees());
        plan.setMaxStorageMb(dto.getMaxStorageMb());
        plan.setTrialDays(dto.getTrialDays());
        plan.setTrial(dto.isTrial());
        plan.setValidityMonths(dto.getValidityMonths());
        plan.setActive(dto.isActive());
        plan.setDescription(dto.getDescription());

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        log.info("REST request to delete subscription plan ID: {}", id);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));
        
        if (plan.isTrial()) {
            throw new IllegalArgumentException("Cannot delete the default Trial plan");
        }
        
        subscriptionPlanRepository.delete(plan);
        return ResponseEntity.noContent().build();
    }

    private SubscriptionPlanDTO convertToDTO(SubscriptionPlan plan) {
        if (plan == null) return null;
        return SubscriptionPlanDTO.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .monthlyPrice(plan.getMonthlyPrice())
                .maxEmployees(plan.getMaxEmployees())
                .maxStorageMb(plan.getMaxStorageMb())
                .trialDays(plan.getTrialDays())
                .isTrial(plan.isTrial())
                .validityMonths(plan.getValidityMonths())
                .isActive(plan.isActive())
                .description(plan.getDescription())
                .build();
    }

    private SubscriptionPlan convertToEntity(SubscriptionPlanDTO dto) {
        if (dto == null) return null;
        return SubscriptionPlan.builder()
                .code(dto.getCode().toLowerCase().trim())
                .name(dto.getName())
                .monthlyPrice(dto.getMonthlyPrice())
                .maxEmployees(dto.getMaxEmployees())
                .maxStorageMb(dto.getMaxStorageMb())
                .trialDays(dto.getTrialDays())
                .isTrial(dto.isTrial())
                .validityMonths(dto.getValidityMonths())
                .isActive(dto.isActive())
                .description(dto.getDescription())
                .build();
    }
}
