package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    Optional<TenantSubscription> findByTenantIdAndIsActiveTrue(Long tenantId);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<TenantSubscription> findByPlanStatusAndTrialEndsAtBefore(String planStatus, LocalDateTime date);
}
