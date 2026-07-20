package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSubscriptionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantSubscriptionEventRepository extends JpaRepository<TenantSubscriptionEvent, UUID> {
    List<TenantSubscriptionEvent> findByTenantIdOrderByTimestampDesc(Long tenantId);
    Page<TenantSubscriptionEvent> findByTenantId(Long tenantId, Pageable pageable);
}
