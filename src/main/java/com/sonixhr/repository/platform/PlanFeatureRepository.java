package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {
    List<PlanFeature> findBySubscriptionPlanId(Long planId);

    Optional<PlanFeature> findBySubscriptionPlanIdAndFeatureCode(Long planId, String featureCode);
}
