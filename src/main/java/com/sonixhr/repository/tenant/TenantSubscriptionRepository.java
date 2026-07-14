package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    Optional<TenantSubscription> findByTenantIdAndIsActiveTrue(Long tenantId);
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("SELECT s FROM TenantSubscription s JOIN FETCH s.tenant JOIN FETCH s.subscriptionPlan WHERE s.isActive = true")
    List<TenantSubscription> findAllActiveWithTenantAndPlan();

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.billingPeriodEnd BETWEEN :start AND :end")
    Page<TenantSubscription> findExpiringSubscriptionsBetween(
            @Param("status") PlanStatus status, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end, 
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.billingPeriodEnd < :dateTime")
    Page<TenantSubscription> findExpiredSubscriptionsBefore(
            @Param("status") PlanStatus status, 
            @Param("dateTime") LocalDateTime dateTime, 
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.autoRenew = true AND s.billingPeriodEnd < :dateTime")
    Page<TenantSubscription> findAutoRenewSubscriptionsBefore(
            @Param("status") PlanStatus status, 
            @Param("dateTime") LocalDateTime dateTime, 
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.gracePeriodEnd < :dateTime")
    Page<TenantSubscription> findGracePeriodExpiredSubscriptions(
            @Param("status") PlanStatus status, 
            @Param("dateTime") LocalDateTime dateTime, 
            Pageable pageable);

    Optional<TenantSubscription> findByTenantIdAndIsCurrentTrue(Long tenantId);
}
