package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<TenantSubscription> findByTenantIdAndIsActiveTrue(Long tenantId);

    Optional<TenantSubscription> findByTenantIdAndIsCurrentTrue(Long tenantId);

    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.isCurrent = true")
    Optional<TenantSubscription> findCurrentByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.isActive = true AND s.planStatus = 'ACTIVE'")
    Optional<TenantSubscription> findActiveByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId ORDER BY s.createdAt DESC")
    List<TenantSubscription> findAllByTenantIdOrderByCreatedAtDesc(@Param("tenantId") Long tenantId);

    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId ORDER BY s.createdAt DESC LIMIT 1")
    Optional<TenantSubscription> findLatestByTenantId(@Param("tenantId") Long tenantId);

    // =====================================================
    // EXISTS CHECKS
    // =====================================================

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.isActive = true AND s.planStatus = 'ACTIVE'")
    boolean existsActiveSubscription(@Param("tenantId") Long tenantId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.isCurrent = true")
    boolean existsCurrentSubscription(@Param("tenantId") Long tenantId);

    // =====================================================
    // EXPIRATION QUERIES
    // =====================================================

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.billingPeriodEnd BETWEEN :start AND :end AND s.isActive = true")
    Page<TenantSubscription> findExpiringSubscriptionsBetween(
            @Param("status") PlanStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = 'ACTIVE' AND s.billingPeriodEnd BETWEEN :start AND :end AND s.isActive = true")
    List<TenantSubscription> findActiveSubscriptionsExpiringBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.billingPeriodEnd < :dateTime AND s.isActive = true")
    Page<TenantSubscription> findExpiredSubscriptionsBefore(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime,
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.billingPeriodEnd < :dateTime AND s.isActive = true")
    List<TenantSubscription> findAllExpiredSubscriptionsBefore(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime);

    // FIXED: Added tenantId parameter to the query
    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.planStatus = :status AND s.isActive = true AND s.billingPeriodEnd < :date")
    List<TenantSubscription> findExpiredSubscriptionsByTenantId(
            @Param("tenantId") Long tenantId,
            @Param("status") PlanStatus status,
            @Param("date") LocalDateTime date);

    // =====================================================
    // AUTO-RENEWAL QUERIES
    // =====================================================

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.autoRenew = true AND s.billingPeriodEnd < :dateTime AND s.isActive = true")
    Page<TenantSubscription> findAutoRenewSubscriptionsBefore(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime,
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.autoRenew = true AND s.billingPeriodEnd < :dateTime AND s.isActive = true")
    List<TenantSubscription> findAllAutoRenewSubscriptionsBefore(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime);

    // =====================================================
    // GRACE PERIOD QUERIES
    // =====================================================

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.gracePeriodEnd < :dateTime")
    Page<TenantSubscription> findGracePeriodExpiredSubscriptions(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime,
            Pageable pageable);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status AND s.gracePeriodEnd < :dateTime AND s.isActive = true")
    List<TenantSubscription> findAllGracePeriodExpiredSubscriptions(
            @Param("status") PlanStatus status,
            @Param("dateTime") LocalDateTime dateTime);

    @Query("SELECT s FROM TenantSubscription s WHERE s.gracePeriodEnd IS NOT NULL AND s.gracePeriodEnd > :dateTime AND s.planStatus = 'PAST_DUE'")
    List<TenantSubscription> findSubscriptionsInGracePeriod(@Param("dateTime") LocalDateTime dateTime);

    // =====================================================
    // JOIN FETCH QUERIES
    // =====================================================

    @Query("SELECT s FROM TenantSubscription s JOIN FETCH s.tenant JOIN FETCH s.subscriptionPlan WHERE s.isActive = true")
    List<TenantSubscription> findAllActiveWithTenantAndPlan();

    @Query("SELECT s FROM TenantSubscription s JOIN FETCH s.tenant JOIN FETCH s.subscriptionPlan WHERE s.tenant.id = :tenantId AND s.isCurrent = true")
    Optional<TenantSubscription> findCurrentWithTenantAndPlan(@Param("tenantId") Long tenantId);

    @Query("SELECT s FROM TenantSubscription s JOIN FETCH s.tenant JOIN FETCH s.subscriptionPlan WHERE s.id = :id")
    Optional<TenantSubscription> findByIdWithTenantAndPlan(@Param("id") Long id);

    // =====================================================
    // STATUS QUERIES
    // =====================================================

    @Query("SELECT s FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.planStatus IN :statuses ORDER BY s.createdAt DESC")
    List<TenantSubscription> findByTenantIdAndStatusIn(
            @Param("tenantId") Long tenantId,
            @Param("statuses") List<PlanStatus> statuses);

    @Query("SELECT s FROM TenantSubscription s WHERE s.planStatus = :status ORDER BY s.createdAt DESC")
    Page<TenantSubscription> findByStatus(@Param("status") PlanStatus status, Pageable pageable);

    @Query("SELECT COUNT(s) FROM TenantSubscription s WHERE s.planStatus = :status")
    long countByStatus(@Param("status") PlanStatus status);

    @Query("SELECT COUNT(s) FROM TenantSubscription s WHERE s.tenant.id = :tenantId AND s.isActive = true")
    long countActiveByTenantId(@Param("tenantId") Long tenantId);

    // =====================================================
    // UPDATE QUERIES
    // =====================================================

    @Modifying
    @Query("UPDATE TenantSubscription s SET s.isActive = false, s.isCurrent = false WHERE s.tenant.id = :tenantId AND s.isActive = true")
    void deactivateAllActiveSubscriptions(@Param("tenantId") Long tenantId);

    @Modifying
    @Query("UPDATE TenantSubscription s SET s.isCurrent = false WHERE s.tenant.id = :tenantId AND s.isCurrent = true")
    void unsetCurrentSubscription(@Param("tenantId") Long tenantId);

    @Modifying
    @Query("UPDATE TenantSubscription s SET s.planStatus = :status WHERE s.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") PlanStatus status);

    // =====================================================
    // ANALYTICS QUERIES
    // =====================================================

    @Query("SELECT COUNT(s) FROM TenantSubscription s WHERE s.planStatus = 'ACTIVE' AND s.billingPeriodEnd BETWEEN :start AND :end")
    long countActiveSubscriptionsInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT s.subscriptionPlan.id, COUNT(s) FROM TenantSubscription s WHERE s.planStatus = 'ACTIVE' GROUP BY s.subscriptionPlan.id")
    List<Object[]> countActiveSubscriptionsByPlan();

    @Query("SELECT FUNCTION('DATE', s.createdAt), COUNT(s) FROM TenantSubscription s WHERE s.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', s.createdAt)")
    List<Object[]> countSubscriptionsByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Page<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
}