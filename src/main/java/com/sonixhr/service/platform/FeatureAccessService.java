package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlanFeature;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.platform.PlanFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final TenantRepository tenantRepository;
    private final PlanFeatureRepository planFeatureRepository;

    @Cacheable(value = "planFeatures", key = "#planId", unless = "#result == null")
    public List<PlanFeature> getFeaturesByPlanId(Long planId) {
        log.debug("Loading plan features from database for cache: planId={}", planId);
        return planFeatureRepository.findBySubscriptionPlanId(planId);
    }

    public boolean hasFeature(Long tenantId, String featureCode) {
        if (tenantId == null || featureCode == null) {
            return false;
        }

        // Check base feature
        boolean hasBaseFeature = checkFeatureDirect(tenantId, featureCode);
        if (!hasBaseFeature) {
            return false;
        }

        // Check dependencies recursively
        List<String> dependencies = getFeatureDependencies(featureCode);
        for (String dep : dependencies) {
            if (!hasFeature(tenantId, dep)) {
                log.warn("Access to feature '{}' blocked because dependency '{}' is missing or disabled for tenant: {}", featureCode, dep, tenantId);
                return false;
            }
        }

        return true;
    }

    private boolean checkFeatureDirect(Long tenantId, String featureCode) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> {
                    if (tenant.getSubscriptionPlan() == null) {
                        return false;
                    }
                    List<PlanFeature> features = getFeaturesByPlanId(tenant.getSubscriptionPlan().getId());
                    return features.stream()
                            .filter(pf -> featureCode.equalsIgnoreCase(pf.getFeatureCode()))
                            .map(PlanFeature::isEnabled)
                            .findFirst()
                            .orElse(false);
                })
                .orElse(false);
    }

    private List<String> getFeatureDependencies(String featureCode) {
        if ("WEBHOOK_ACCESS".equalsIgnoreCase(featureCode)) {
            return List.of("API_ACCESS");
        }
        return List.of();
    }
}
