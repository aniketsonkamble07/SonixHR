package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlanFeature;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlanFeatureRepository;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanFeatureService {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    // =====================================================
    // CREATE / UPDATE FEATURES
    // =====================================================

    /**
     * Add a feature to a plan
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature addFeatureToPlan(Long planId, String featureCode, String description) {
        log.info("Adding feature '{}' to plan ID: {}", featureCode, planId);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));

        // Check if feature already exists
        if (planFeatureRepository.existsBySubscriptionPlanIdAndFeatureCodeIgnoreCase(planId, featureCode)) {
            throw new IllegalArgumentException("Feature '" + featureCode + "' already exists for this plan");
        }

        PlanFeature feature = PlanFeature.builder()
                .subscriptionPlan(plan)
                .featureCode(featureCode)
                .description(description)
                .isEnabled(true)
                .displayOrder(0)
                .build();

        PlanFeature saved = planFeatureRepository.save(feature);
        log.info("Added feature '{}' to plan ID: {}", featureCode, planId);
        return saved;
    }

    /**
     * Add multiple features to a plan
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public List<PlanFeature> addFeaturesToPlan(Long planId, Set<String> featureCodes) {
        log.info("Adding {} features to plan ID: {}", featureCodes.size(), planId);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));

        List<PlanFeature> features = featureCodes.stream()
                .filter(code -> !planFeatureRepository.existsBySubscriptionPlanIdAndFeatureCodeIgnoreCase(planId, code))
                .map(code -> PlanFeature.builder()
                        .subscriptionPlan(plan)
                        .featureCode(code)
                        .isEnabled(true)
                        .displayOrder(0)
                        .build())
                .collect(Collectors.toList());

        List<PlanFeature> saved = planFeatureRepository.saveAll(features);
        log.info("Added {} features to plan ID: {}", saved.size(), planId);
        return saved;
    }

    /**
     * Update feature description
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature updateFeatureDescription(Long planId, Long featureId, String description) {
        log.info("Updating feature ID: {} description for plan ID: {}", featureId, planId);

        PlanFeature feature = planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

        if (!feature.getSubscriptionPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
        }

        feature.setDescription(description);
        return planFeatureRepository.save(feature);
    }

    // =====================================================
    // TOGGLE FEATURES
    // =====================================================

    /**
     * Enable a feature
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature enableFeature(Long planId, Long featureId) {
        log.info("Enabling feature ID: {} for plan ID: {}", featureId, planId);

        PlanFeature feature = planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

        if (!feature.getSubscriptionPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
        }

        feature.setIsEnabled(true);
        return planFeatureRepository.save(feature);
    }

    /**
     * Disable a feature
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature disableFeature(Long planId, Long featureId) {
        log.info("Disabling feature ID: {} for plan ID: {}", featureId, planId);

        PlanFeature feature = planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

        if (!feature.getSubscriptionPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
        }

        feature.setIsEnabled(false);
        return planFeatureRepository.save(feature);
    }

    /**
     * Toggle feature status
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature toggleFeature(Long planId, Long featureId) {
        log.info("Toggling feature ID: {} for plan ID: {}", featureId, planId);

        PlanFeature feature = planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

        if (!feature.getSubscriptionPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
        }

        feature.setIsEnabled(!feature.isEnabled());
        return planFeatureRepository.save(feature);
    }

    // =====================================================
    // DELETE FEATURES
    // =====================================================

    /**
     * Delete a feature from a plan (hard delete)
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public void deleteFeature(Long planId, Long featureId) {
        log.info("Deleting feature ID: {} from plan ID: {}", featureId, planId);

        PlanFeature feature = planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

        if (!feature.getSubscriptionPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
        }

        planFeatureRepository.delete(feature);
        log.info("Deleted feature ID: {} from plan ID: {}", featureId, planId);
    }

    /**
     * Delete all features from a plan
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public void deleteAllFeatures(Long planId) {
        log.info("Deleting all features for plan ID: {}", planId);
        planFeatureRepository.deleteAllByPlanId(planId);
        log.info("Deleted all features for plan ID: {}", planId);
    }

    // =====================================================
    // QUERY FEATURES
    // =====================================================

    /**
     * Get all enabled features for a plan (cached)
     */
    @Cacheable(value = "planFeatures", key = "#planId")
    public Set<String> getEnabledFeatureCodes(Long planId) {
        log.debug("Fetching enabled features for plan ID: {} (cache miss)", planId);
        return planFeatureRepository.findEnabledFeatureCodesByPlanId(planId);
    }

    /**
     * Get all features for a plan
     */
    public List<PlanFeature> getFeaturesByPlanId(Long planId) {
        log.debug("Fetching all features for plan ID: {}", planId);
        return planFeatureRepository.findAllFeaturesByPlanId(planId);
    }

    /**
     * Get only enabled features for a plan
     */
    public List<PlanFeature> getEnabledFeaturesByPlanId(Long planId) {
        log.debug("Fetching enabled features for plan ID: {}", planId);
        return planFeatureRepository.findEnabledFeaturesByPlanId(planId);
    }

    /**
     * Check if a plan has a specific feature
     */
    public boolean hasFeature(Long planId, String featureCode) {
        log.debug("Checking if plan ID: {} has feature: {}", planId, featureCode);
        return planFeatureRepository.existsBySubscriptionPlanIdAndFeatureCodeIgnoreCase(planId, featureCode);
    }

    /**
     * Check if a plan has a specific enabled feature
     */
    public boolean hasEnabledFeature(Long planId, String featureCode) {
        log.debug("Checking if plan ID: {} has enabled feature: {}", planId, featureCode);
        Set<String> enabledFeatures = getEnabledFeatureCodes(planId);
        return enabledFeatures.contains(featureCode);
    }

    /**
     * Get feature by ID
     */
    public PlanFeature getFeatureById(Long featureId) {
        log.debug("Fetching feature by ID: {}", featureId);
        return planFeatureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));
    }

    /**
     * Get feature by plan ID and feature code
     */
    public PlanFeature getFeatureByPlanAndCode(Long planId, String featureCode) {
        log.debug("Fetching feature for plan ID: {} and code: {}", planId, featureCode);
        return planFeatureRepository.findAllFeaturesByPlanId(planId).stream()
                .filter(f -> f.getFeatureCode().equalsIgnoreCase(featureCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature not found for plan ID: " + planId + " and code: " + featureCode));
    }

    // =====================================================
    // UPDATE FEATURE ORDER
    // =====================================================

    /**
     * Update display order of features
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public void updateFeatureOrder(Long planId, List<Long> featureIdsInOrder) {
        log.info("Updating feature order for plan ID: {}", planId);

        for (int i = 0; i < featureIdsInOrder.size(); i++) {
            Long featureId = featureIdsInOrder.get(i);
            PlanFeature feature = planFeatureRepository.findById(featureId)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found with ID: " + featureId));

            if (!feature.getSubscriptionPlan().getId().equals(planId)) {
                throw new IllegalArgumentException("Feature does not belong to plan ID: " + planId);
            }

            feature.setDisplayOrder(i);
            planFeatureRepository.save(feature);
        }

        log.info("Updated order for {} features for plan ID: {}", featureIdsInOrder.size(), planId);
    }

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    /**
     * Replace all features for a plan
     */
    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public List<PlanFeature> replaceFeatures(Long planId, Set<String> newFeatureCodes) {
        log.info("Replacing all features for plan ID: {}", planId);

        // Delete all existing features
        planFeatureRepository.deleteAllByPlanId(planId);

        // Add new features
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));

        if (newFeatureCodes != null && !newFeatureCodes.isEmpty()) {
            List<PlanFeature> features = newFeatureCodes.stream()
                    .map(code -> PlanFeature.builder()
                            .subscriptionPlan(plan)
                            .featureCode(code)
                            .isEnabled(true)
                            .displayOrder(0)
                            .build())
                    .collect(Collectors.toList());

            List<PlanFeature> saved = planFeatureRepository.saveAll(features);
            log.info("Replaced features for plan ID: {}. New features: {}", planId, saved.size());
            return saved;
        }

        log.info("Replaced features for plan ID: {}. No features added.", planId);
        return List.of();
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    /**
     * Get count of features for a plan
     */
    public long countFeaturesByPlanId(Long planId) {
        return planFeatureRepository.findAllFeaturesByPlanId(planId).size();
    }

    /**
     * Get count of enabled features for a plan
     */
    public long countEnabledFeaturesByPlanId(Long planId) {
        return planFeatureRepository.findEnabledFeaturesByPlanId(planId).size();
    }

    /**
     * Get all feature codes for a plan (both enabled and disabled)
     */
    public Set<String> getAllFeatureCodes(Long planId) {
        log.debug("Fetching all feature codes for plan ID: {}", planId);
        return planFeatureRepository.findAllFeaturesByPlanId(planId).stream()
                .map(PlanFeature::getFeatureCode)
                .collect(Collectors.toSet());
    }
}