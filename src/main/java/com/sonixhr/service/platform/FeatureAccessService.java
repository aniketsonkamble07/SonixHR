package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlanFeature;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.platform.PlanFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final TenantRepository tenantRepository;
    private final PlanFeatureRepository planFeatureRepository;

    // Feature dependency map (cached in memory for performance)
    private final Map<String, List<String>> featureDependencyCache = new ConcurrentHashMap<>();

    // Feature hierarchy for complex dependencies
    private static final Map<String, List<String>> FEATURE_DEPENDENCIES = new HashMap<>();

    static {
        // Define feature dependencies
        FEATURE_DEPENDENCIES.put("WEBHOOK_ACCESS", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("ADVANCED_REPORTING", List.of("BASIC_REPORTING"));
        FEATURE_DEPENDENCIES.put("CUSTOM_WORKFLOWS", List.of("API_ACCESS", "ADVANCED_REPORTING"));
        FEATURE_DEPENDENCIES.put("SSO", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("AUDIT_LOGS", List.of("ADVANCED_REPORTING"));
        FEATURE_DEPENDENCIES.put("DATA_EXPORT", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("WHITE_LABEL", List.of("API_ACCESS", "SSO"));
        FEATURE_DEPENDENCIES.put("PERFORMANCE_REVIEWS", List.of("ADVANCED_REPORTING"));
        FEATURE_DEPENDENCIES.put("RECRUITMENT", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("ONBOARDING", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("DEDICATED_SUPPORT", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("CUSTOM_ROLES", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("BULK_IMPORT", List.of("API_ACCESS"));
        FEATURE_DEPENDENCIES.put("API_ACCESS", List.of()); // No dependencies
        FEATURE_DEPENDENCIES.put("BASIC_REPORTING", List.of()); // No dependencies
    }

    // =====================================================
    // FEATURE CHECK METHODS
    // =====================================================

    /**
     * Check if a tenant has access to a specific feature
     */
    @Cacheable(value = "featureAccess", key = "#tenantId + ':' + #featureCode", unless = "#result == null")
    public boolean hasFeature(Long tenantId, String featureCode) {
        if (tenantId == null || featureCode == null) {
            log.debug("Feature check failed: tenantId or featureCode is null");
            return false;
        }

        log.debug("Checking feature access for tenant: {}, feature: {}", tenantId, featureCode);

        // Get tenant and check subscription
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            log.debug("Tenant not found: {}", tenantId);
            return false;
        }

        Tenant tenant = tenantOpt.get();

        // Check if tenant is active and has valid subscription
        if (!isTenantActive(tenant)) {
            log.debug("Tenant {} is not active or has expired subscription", tenantId);
            return false;
        }

        // Check if feature exists directly
        boolean hasDirectFeature = checkFeatureDirect(tenant, featureCode);
        if (!hasDirectFeature) {
            log.debug("Tenant {} does not have direct access to feature: {}", tenantId, featureCode);
            return false;
        }

        // Check dependencies recursively
        List<String> dependencies = getFeatureDependencies(featureCode);
        for (String dep : dependencies) {
            if (!hasFeature(tenantId, dep)) {
                log.warn("Access to feature '{}' blocked because dependency '{}' is missing for tenant: {}",
                        featureCode, dep, tenantId);
                return false;
            }
        }

        log.debug("Tenant {} has access to feature: {}", tenantId, featureCode);
        return true;
    }

    /**
     * Check if a tenant has access to ANY of the specified features
     */
    public boolean hasAnyFeature(Long tenantId, String... featureCodes) {
        if (tenantId == null || featureCodes == null || featureCodes.length == 0) {
            return false;
        }

        for (String featureCode : featureCodes) {
            if (hasFeature(tenantId, featureCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a tenant has access to ALL of the specified features
     */
    public boolean hasAllFeatures(Long tenantId, String... featureCodes) {
        if (tenantId == null || featureCodes == null || featureCodes.length == 0) {
            return false;
        }

        for (String featureCode : featureCodes) {
            if (!hasFeature(tenantId, featureCode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get all features available to a tenant
     */
    public Set<String> getAvailableFeatures(Long tenantId) {
        if (tenantId == null) {
            return Collections.emptySet();
        }

        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            return Collections.emptySet();
        }

        Tenant tenant = tenantOpt.get();
        if (!isTenantActive(tenant)) {
            return Collections.emptySet();
        }

        if (tenant.getSubscriptionPlan() == null) {
            return Collections.emptySet();
        }

        List<PlanFeature> features = getFeaturesByPlanId(tenant.getSubscriptionPlan().getId());
        Set<String> availableFeatures = new HashSet<>();

        for (PlanFeature feature : features) {
            if (feature.isEnabled()) {
                // Check if all dependencies are met
                boolean hasAllDeps = true;
                for (String dep : getFeatureDependencies(feature.getFeatureCode())) {
                    if (!features.stream()
                            .anyMatch(f -> f.getFeatureCode().equalsIgnoreCase(dep) && f.isEnabled())) {
                        hasAllDeps = false;
                        break;
                    }
                }
                if (hasAllDeps) {
                    availableFeatures.add(feature.getFeatureCode());
                }
            }
        }

        return availableFeatures;
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    /**
     * Check if a tenant is active and has a valid subscription
     */
    private boolean isTenantActive(Tenant tenant) {
        if (tenant == null) {
            return false;
        }

        // Check tenant status
        if (tenant.getStatus() == null) {
            return false;
        }

        // Check if tenant is not deleted or suspended
        if (tenant.getStatus().isInactive()) {
            return false;
        }

        // Check subscription status
        if (tenant.getPlanStatus() == null) {
            return false;
        }

        // Active statuses are allowed
        return tenant.getPlanStatus() == PlanStatus.ACTIVE ;
    }

    /**
     * Check direct feature access (without dependencies)
     */
    private boolean checkFeatureDirect(Tenant tenant, String featureCode) {
        if (tenant == null || tenant.getSubscriptionPlan() == null) {
            return false;
        }

        List<PlanFeature> features = getFeaturesByPlanId(tenant.getSubscriptionPlan().getId());
        return features.stream()
                .filter(PlanFeature::isEnabled)
                .anyMatch(pf -> featureCode.equalsIgnoreCase(pf.getFeatureCode()));
    }

    /**
     * Get feature dependencies
     */
    private List<String> getFeatureDependencies(String featureCode) {
        if (featureCode == null) {
            return Collections.emptyList();
        }

        // Check cache first
        String upperCode = featureCode.toUpperCase();
        if (featureDependencyCache.containsKey(upperCode)) {
            return featureDependencyCache.get(upperCode);
        }

        // Get from static map
        List<String> dependencies = FEATURE_DEPENDENCIES.getOrDefault(
                upperCode,
                Collections.emptyList()
        );

        // Cache the result
        featureDependencyCache.put(upperCode, dependencies);
        return dependencies;
    }

    // =====================================================
    // PLAN FEATURE QUERIES
    // =====================================================

    /**
     * Get all features for a plan (cached)
     */
    @Cacheable(value = "planFeatures", key = "#planId", unless = "#result == null")
    public List<PlanFeature> getFeaturesByPlanId(Long planId) {
        log.debug("Loading plan features from database for planId: {}", planId);
        return planFeatureRepository.findBySubscriptionPlanId(planId);
    }

    /**
     * Get enabled feature codes for a plan
     */
    @Cacheable(value = "planFeatureCodes", key = "#planId", unless = "#result == null")
    public Set<String> getEnabledFeatureCodes(Long planId) {
        log.debug("Loading enabled feature codes for planId: {}", planId);
        List<PlanFeature> features = getFeaturesByPlanId(planId);
        Set<String> enabledCodes = new HashSet<>();
        for (PlanFeature feature : features) {
            if (feature.isEnabled()) {
                enabledCodes.add(feature.getFeatureCode());
            }
        }
        return enabledCodes;
    }

    // =====================================================
    // FEATURE VALIDATION
    // =====================================================

    /**
     * Validate if a tenant can use a specific feature (throws exception if not)
     */
    public void validateFeatureAccess(Long tenantId, String featureCode) {
        if (!hasFeature(tenantId, featureCode)) {
            throw new IllegalStateException(
                    String.format("Tenant %d does not have access to feature: %s", tenantId, featureCode)
            );
        }
    }

    /**
     * Validate if a tenant can use any of the specified features
     */
    public void validateAnyFeatureAccess(Long tenantId, String... featureCodes) {
        if (!hasAnyFeature(tenantId, featureCodes)) {
            throw new IllegalStateException(
                    String.format("Tenant %d does not have access to any of the required features: %s",
                            tenantId, String.join(", ", featureCodes))
            );
        }
    }

    // =====================================================
    // BULK FEATURE CHECK
    // =====================================================

    /**
     * Check multiple features at once
     */
    public Map<String, Boolean> checkFeatures(Long tenantId, String... featureCodes) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String featureCode : featureCodes) {
            result.put(featureCode, hasFeature(tenantId, featureCode));
        }
        return result;
    }

    /**
     * Get feature access summary for a tenant
     */
    public FeatureAccessSummary getFeatureAccessSummary(Long tenantId) {
        if (tenantId == null) {
            return FeatureAccessSummary.empty();
        }

        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            return FeatureAccessSummary.empty();
        }

        Tenant tenant = tenantOpt.get();
        Set<String> availableFeatures = getAvailableFeatures(tenantId);
        Set<String> allFeatures = getAllFeatureCodes();

        // Calculate missing features
        Set<String> missingFeatures = new HashSet<>();
        for (String feature : allFeatures) {
            if (!availableFeatures.contains(feature)) {
                missingFeatures.add(feature);
            }
        }

        return FeatureAccessSummary.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getCompanyName())
                .planName(tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getName() : "No Plan")
                .planStatus(tenant.getPlanStatus())
                .availableFeatures(availableFeatures)
                .allFeatures(allFeatures)
                .missingFeatures(missingFeatures)
                .build();
    }

    // =====================================================
    // FEATURE DEFINITIONS
    // =====================================================

    /**
     * Get all defined feature codes
     */
    public Set<String> getAllFeatureCodes() {
        return FEATURE_DEPENDENCIES.keySet();
    }

    /**
     * Get feature dependencies for a feature
     */
    public List<String> getFeatureDependencies(String featureCode, boolean includeTransitive) {
        if (!includeTransitive) {
            return getFeatureDependencies(featureCode);
        }

        // Get transitive dependencies
        Set<String> allDeps = new HashSet<>();
        collectDependencies(featureCode, allDeps);
        return new ArrayList<>(allDeps);
    }

    private void collectDependencies(String featureCode, Set<String> collected) {
        List<String> deps = getFeatureDependencies(featureCode);
        for (String dep : deps) {
            if (!collected.contains(dep)) {
                collected.add(dep);
                collectDependencies(dep, collected);
            }
        }
    }

    // =====================================================
    // FEATURE ACCESS SUMMARY DTO
    // =====================================================

    @lombok.Builder
    @lombok.Data
    public static class FeatureAccessSummary {
        private Long tenantId;
        private String tenantName;
        private String planName;
        private PlanStatus planStatus;
        private Set<String> availableFeatures;
        private Set<String> allFeatures;
        private Set<String> missingFeatures;

        public static FeatureAccessSummary empty() {
            return FeatureAccessSummary.builder()
                    .tenantId(null)
                    .tenantName("Unknown")
                    .planName("No Plan")
                    .planStatus(null)
                    .availableFeatures(Collections.emptySet())
                    .allFeatures(Collections.emptySet())
                    .missingFeatures(Collections.emptySet())
                    .build();
        }

        public int getAvailableCount() {
            return availableFeatures != null ? availableFeatures.size() : 0;
        }

        public int getTotalCount() {
            return allFeatures != null ? allFeatures.size() : 0;
        }

        public int getMissingCount() {
            return missingFeatures != null ? missingFeatures.size() : 0;
        }

        public double getAccessPercentage() {
            int total = getTotalCount();
            if (total == 0) return 0.0;
            return (double) getAvailableCount() / total * 100;
        }
    }
}