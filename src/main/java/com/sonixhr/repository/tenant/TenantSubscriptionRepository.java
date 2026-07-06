package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    Optional<TenantSubscription> findByTenantIdAndIsActiveTrue(Long tenantId);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
