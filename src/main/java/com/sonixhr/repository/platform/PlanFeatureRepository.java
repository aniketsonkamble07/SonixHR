package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    @Query("SELECT pf FROM PlanFeature pf WHERE pf.subscriptionPlan.id = :planId AND pf.isEnabled = true")
    List<PlanFeature> findEnabledFeaturesByPlanId(@Param("planId") Long planId);

    @Query("SELECT pf FROM PlanFeature pf WHERE pf.subscriptionPlan.id = :planId")
    List<PlanFeature> findAllFeaturesByPlanId(@Param("planId") Long planId);

    @Query("SELECT pf.featureCode FROM PlanFeature pf WHERE pf.subscriptionPlan.id = :planId AND pf.isEnabled = true")
    Set<String> findEnabledFeatureCodesByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("DELETE FROM PlanFeature pf WHERE pf.subscriptionPlan.id = :planId")
    void deleteAllByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("UPDATE PlanFeature pf SET pf.isEnabled = :enabled WHERE pf.subscriptionPlan.id = :planId AND pf.featureCode = :featureCode")
    void updateFeatureStatus(@Param("planId") Long planId, @Param("featureCode") String featureCode, @Param("enabled") boolean enabled);

    boolean existsBySubscriptionPlanIdAndFeatureCodeIgnoreCase(Long planId, String featureCode);

    @Query("SELECT pf FROM PlanFeature pf WHERE pf.subscriptionPlan.id = :planId")
    List<PlanFeature> findBySubscriptionPlanId(@Param("planId") Long planId);
}