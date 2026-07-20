package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlanFeature;
import com.sonixhr.repository.platform.PlanFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanFeatureService {

    private final PlanFeatureRepository planFeatureRepository;

    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public PlanFeature savePlanFeature(Long planId, PlanFeature planFeature) {
        log.info("Saving plan feature and evicting planFeatures cache for planId: {}", planId);
        return planFeatureRepository.save(planFeature);
    }

    @Transactional
    @CacheEvict(value = "planFeatures", key = "#planId")
    public void deletePlanFeature(Long planId, UUID planFeatureId) {
        log.info("Deleting plan feature and evicting planFeatures cache for planId: {}", planId);
        planFeatureRepository.deleteById(planFeatureId);
    }
}
