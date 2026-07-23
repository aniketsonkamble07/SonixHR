package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.SubscriptionEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionEventLogRepository extends JpaRepository<SubscriptionEventLog, Long> {

    @Query("SELECT e FROM SubscriptionEventLog e WHERE e.tenant.id = :tenantId ORDER BY e.createdAt DESC")
    List<SubscriptionEventLog> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT e FROM SubscriptionEventLog e WHERE e.subscription.id = :subscriptionId ORDER BY e.createdAt DESC")
    List<SubscriptionEventLog> findBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Query("SELECT e FROM SubscriptionEventLog e WHERE e.eventType = :eventType ORDER BY e.createdAt DESC")
    List<SubscriptionEventLog> findByEventType(@Param("eventType") String eventType);

    @Query("SELECT e FROM SubscriptionEventLog e WHERE e.tenant.id = :tenantId ORDER BY e.createdAt DESC")
    Page<SubscriptionEventLog> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
}