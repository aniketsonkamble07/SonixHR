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
        log.info("REST request to list all active subscription plans");
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAllActivePlans();
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
        if (plan.isDeleted()) {
            throw new ResourceNotFoundException("Subscription plan not found");
        }
        return ResponseEntity.ok(convertToDTO(plan));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@RequestBody @Valid SubscriptionPlanDTO dto) {
        log.info("REST request to create subscription plan: {}", dto.getCode());

        if (subscriptionPlanRepository.findByNameIgnoreCase(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Plan with name '" + dto.getName() + "' already exists");
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

        if (plan.isDeleted()) {
            throw new ResourceNotFoundException("Subscription plan not found");
        }

        plan.setName(dto.getName());
        plan.setPrice(dto.getPrice());
        plan.setValidityMonths(dto.getValidityMonths());
        plan.setActive(dto.isActive());
        plan.setDescription(dto.getDescription());
        plan.setMaxEmployees(dto.getMaxEmployees());
        plan.setMaxUsers(dto.getMaxUsers());

        // Sync plan features (clear and replace)
        if (plan.getPlanFeatures() != null) {
            plan.getPlanFeatures().clear();
        } else {
            plan.setPlanFeatures(new java.util.HashSet<>());
        }
        if (dto.getEnabledFeatures() != null) {
            for (String code : dto.getEnabledFeatures()) {
                plan.getPlanFeatures().add(
                    com.sonixhr.entity.platform.PlanFeature.builder()
                        .subscriptionPlan(plan)
                        .featureCode(code)
                        .enabled(true)
                        .build()
                );
            }
        }

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRICING_PLANS')")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        log.info("REST request to soft delete subscription plan ID: {}", id);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (plan.isDeleted()) {
            throw new ResourceNotFoundException("Subscription plan not found");
        }

        plan.softDelete();
        subscriptionPlanRepository.save(plan);
        return ResponseEntity.noContent().build();
    }

    private SubscriptionPlanDTO convertToDTO(SubscriptionPlan plan) {
        if (plan == null)
            return null;
        java.util.List<String> features = java.util.Collections.emptyList();
        if (plan.getPlanFeatures() != null) {
            features = plan.getPlanFeatures().stream()
                    .filter(com.sonixhr.entity.platform.PlanFeature::isEnabled)
                    .map(com.sonixhr.entity.platform.PlanFeature::getFeatureCode)
                    .collect(java.util.stream.Collectors.toList());
        }
        return SubscriptionPlanDTO.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .price(plan.getPrice())
                .validityMonths(plan.getValidityMonths())
                .isActive(plan.isActive())
                .description(plan.getDescription())
                .maxEmployees(plan.getMaxEmployees())
                .maxUsers(plan.getMaxUsers())
                .enabledFeatures(features)
                .build();
    }

    private SubscriptionPlan convertToEntity(SubscriptionPlanDTO dto) {
        if (dto == null)
            return null;
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .price(dto.getPrice())
                .validityMonths(dto.getValidityMonths())
                .isActive(dto.isActive())
                .description(dto.getDescription())
                .maxEmployees(dto.getMaxEmployees())
                .maxUsers(dto.getMaxUsers())
                .build();

        if (dto.getEnabledFeatures() != null) {
            java.util.Set<com.sonixhr.entity.platform.PlanFeature> planFeatures = dto.getEnabledFeatures().stream()
                    .map(code -> com.sonixhr.entity.platform.PlanFeature.builder()
                            .subscriptionPlan(plan)
                            .featureCode(code)
                            .enabled(true)
                            .build())
                    .collect(java.util.stream.Collectors.toSet());
            plan.setPlanFeatures(planFeatures);
        }
        return plan;
    }
}
