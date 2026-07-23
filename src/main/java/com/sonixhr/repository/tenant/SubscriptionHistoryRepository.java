// repository/tenant/SubscriptionHistoryRepository.java
package com.sonixhr.repository.tenant;

import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.entity.tenant.SubscriptionHistory;
import com.sonixhr.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    // Basic queries
    Page<SubscriptionHistory> findByTenantIdOrderByEventDateDesc(Long tenantId, Pageable pageable);

    List<SubscriptionHistory> findByTenantIdOrderByEventDateDesc(Long tenantId);

    Optional<SubscriptionHistory> findByIdAndTenantId(Long id, Long tenantId);

    List<SubscriptionHistory> findByTenantIdAndEventDateBetweenOrderByEventDateDesc(
            Long tenantId, LocalDateTime startDate, LocalDateTime endDate);

    List<SubscriptionHistory> findTop10ByTenantIdOrderByEventDateDesc(Long tenantId);

    // Status-based queries
    Page<SubscriptionHistory> findByTenantIdAndPlanStatusOrderByEventDateDesc(
            Long tenantId, PlanStatus status, Pageable pageable);

    List<SubscriptionHistory> findByTenantIdAndPlanStatus(Long tenantId, PlanStatus status);

    // Plan-based queries
    Page<SubscriptionHistory> findByTenantIdAndPlanTypeOrderByEventDateDesc(
            Long tenantId, SubscriptionPlan planType, Pageable pageable);

    // Event type queries
    Page<SubscriptionHistory> findByTenantIdAndEventTypeOrderByEventDateDesc(
            Long tenantId, String eventType, Pageable pageable);

    List<SubscriptionHistory> findByTenantIdAndEventType(Long tenantId, String eventType);

    // Get latest subscription for tenant
    @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId ORDER BY sh.eventDate DESC LIMIT 1")
    Optional<SubscriptionHistory> findLatestByTenantId(@Param("tenantId") Long tenantId);

    // Advanced search with dynamic filters
    @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND (:startDate IS NULL OR sh.eventDate >= :startDate) " +
            "AND (:endDate IS NULL OR sh.eventDate <= :endDate) " +
            "AND (:status IS NULL OR sh.planStatus = :status) " +
            "AND (:planType IS NULL OR sh.planType = :planType) " +
            "AND (:eventType IS NULL OR sh.eventType = :eventType) " +
            "AND (:searchTerm IS NULL OR LOWER(sh.reason) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(sh.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(sh.transactionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(sh.invoiceNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:autoRenew IS NULL OR sh.isAutoRenew = :autoRenew) " +
            "AND (:cancellationType IS NULL OR sh.cancellationType = :cancellationType) " +
            "AND (:minAmount IS NULL OR sh.amount >= :minAmount) " +
            "AND (:maxAmount IS NULL OR sh.amount <= :maxAmount) " +
            "AND (:employeeId IS NULL OR sh.employeeId = :employeeId) " +
            "ORDER BY sh.eventDate DESC")
    Page<SubscriptionHistory> searchSubscriptionHistory(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") PlanStatus status,
            @Param("planType") SubscriptionPlan planType,
            @Param("eventType") String eventType,
            @Param("searchTerm") String searchTerm,
            @Param("autoRenew") Boolean autoRenew,
            @Param("cancellationType") String cancellationType,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("employeeId") Long employeeId,
            Pageable pageable
    );

    // Statistics queries
    @Query("SELECT COUNT(sh) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId AND sh.planStatus = :status")
    Long countByStatus(@Param("tenantId") Long tenantId, @Param("status") PlanStatus status);

    @Query("SELECT COUNT(sh) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId AND sh.eventType = :eventType")
    Long countByEventType(@Param("tenantId") Long tenantId, @Param("eventType") String eventType);

    @Query("SELECT SUM(sh.amount) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND sh.eventDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT AVG(sh.amount) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND sh.eventType = 'RENEWED'")
    BigDecimal avgRenewalAmount(@Param("tenantId") Long tenantId);

    // Group by queries for analytics
    @Query("SELECT sh.eventType, COUNT(sh) FROM SubscriptionHistory sh " +
            "WHERE sh.tenantId = :tenantId AND sh.eventDate BETWEEN :startDate AND :endDate " +
            "GROUP BY sh.eventType")
    List<Object[]> countEventsByTypeGrouped(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT FUNCTION('DATE_FORMAT', sh.eventDate, '%Y-%m'), COUNT(sh) FROM SubscriptionHistory sh " +
            "WHERE sh.tenantId = :tenantId AND sh.eventDate BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE_FORMAT', sh.eventDate, '%Y-%m') " +
            "ORDER BY FUNCTION('DATE_FORMAT', sh.eventDate, '%Y-%m')")
    List<Object[]> countEventsByMonth(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT sh.planType, COUNT(sh) FROM SubscriptionHistory sh " +
            "WHERE sh.tenantId = :tenantId GROUP BY sh.planType")
    List<Object[]> countByPlanType(@Param("tenantId") Long tenantId);

    @Query("SELECT sh.planStatus, COUNT(sh) FROM SubscriptionHistory sh " +
            "WHERE sh.tenantId = :tenantId GROUP BY sh.planStatus")
    List<Object[]> countByStatusGrouped(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(DISTINCT sh.planType) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId")
    Long countDistinctPlansUsed(@Param("tenantId") Long tenantId);

    // Timeline queries
    @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND sh.subscriptionId = :subscriptionId ORDER BY sh.eventDate DESC")
    List<SubscriptionHistory> findBySubscriptionIdOrderByEventDateDesc(
            @Param("tenantId") Long tenantId,
            @Param("subscriptionId") String subscriptionId
    );

    @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND sh.eventDate >= :date AND sh.planStatus = :status")
    List<SubscriptionHistory> findByTenantIdAndEventDateAfterAndStatus(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDateTime date,
            @Param("status") PlanStatus status
    );

    // Revenue analytics
    @Query("SELECT FUNCTION('YEAR', sh.eventDate), FUNCTION('MONTH', sh.eventDate), SUM(sh.amount) " +
            "FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "GROUP BY FUNCTION('YEAR', sh.eventDate), FUNCTION('MONTH', sh.eventDate) " +
            "ORDER BY FUNCTION('YEAR', sh.eventDate) DESC, FUNCTION('MONTH', sh.eventDate) DESC")
    List<Object[]> getMonthlyRevenue(@Param("tenantId") Long tenantId);

    // Churn analytics
    @Query("SELECT COUNT(sh) FROM SubscriptionHistory sh WHERE sh.tenantId = :tenantId " +
            "AND sh.eventType = 'CANCELLED' AND sh.eventDate BETWEEN :startDate AND :endDate")
    Long countCancellationsInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}