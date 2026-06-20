package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByCodeIgnoreCase(String code);
    Optional<SubscriptionPlan> findFirstByIsTrialTrueAndIsActiveTrue();
}
