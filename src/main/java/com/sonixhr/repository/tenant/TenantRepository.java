package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TenantDataStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository // ✅ ADDED
public interface TenantRepository extends JpaRepository<Tenant, Long> {

        // ===== Basic Finders =====
        Optional<Tenant> findByTenantCode(String tenantCode);

        Optional<Tenant> findByCompanyName(String companyName);

        Optional<Tenant> findByCompanyNameIgnoreCase(String companyName);

        // ===== Existence Checks =====
        boolean existsByTenantCode(String tenantCode);

        boolean existsByAdminEmail(String adminEmail);

        boolean existsByCompanyName(String companyName);

        // ===== Paginated Queries =====
        Page<Tenant> findByStatus(String status, Pageable pageable);

        Page<Tenant> findByIsActive(Boolean isActive, Pageable pageable);

        Page<Tenant> findByCompanyNameContainingIgnoreCase(String companyName, Pageable pageable);

        // ===== List Queries =====
        List<Tenant> findByStatus(String status);

        List<Tenant> findByIsActiveTrue();

        // ===== Status Update Methods =====
        @Modifying
        @Transactional
        @Query("UPDATE Tenant t SET t.status = :status, t.isActive = :isActive, t.suspendedAt = :suspendedAt WHERE t.id = :tenantId")
        int updateTenantStatus(@Param("tenantId") Long tenantId,
                        @Param("status") String status,
                        @Param("isActive") Boolean isActive,
                        @Param("suspendedAt") LocalDateTime suspendedAt);

        // ===== Soft Delete Methods =====
        @Modifying
        @Transactional
        @Query("UPDATE Tenant t SET t.status = 'deleted', t.isActive = false, t.deletedAt = CURRENT_TIMESTAMP WHERE t.id = :tenantId")
        int softDeleteById(@Param("tenantId") Long tenantId);

        @Modifying
        @Transactional
        @Query("UPDATE Tenant t SET t.status = 'active', t.isActive = true, t.deletedAt = NULL WHERE t.id = :tenantId")
        int restoreById(@Param("tenantId") Long tenantId);

        // ===== Search with Multiple Criteria =====
        @Query("SELECT t FROM Tenant t WHERE " +
                        "(CAST(:companyName AS string) IS NULL OR LOWER(t.companyName) LIKE LOWER(CONCAT('%', CAST(:companyName AS string), '%'))) AND "
                        +
                        "(CAST(:status AS string) IS NULL OR CAST(t.status AS string) = :status) AND " +
                        "(:isActive IS NULL OR t.isActive = :isActive)")
        Page<Tenant> searchTenants(@Param("companyName") String companyName,
                        @Param("status") String status,
                        @Param("isActive") Boolean isActive,
                        Pageable pageable);

        // ===== Count Queries =====
        long countByStatus(String status);

        long countByIsActiveTrue();

        // ===== Additional Useful Methods =====

        /**
         * Update plan for a tenant
         */
        @Modifying
        @Transactional
        @Query("UPDATE Tenant t SET t.subscriptionPlan = :subscriptionPlan, t.planStatus = :planStatus, " +
                        "t.maxEmployees = :maxEmployees WHERE t.id = :tenantId")
        int updatePlan(@Param("tenantId") Long tenantId,
                        @Param("subscriptionPlan") SubscriptionPlan subscriptionPlan,
                        @Param("planStatus") PlanStatus planStatus,
                        @Param("maxEmployees") Integer maxEmployees);

        /**
         * Count tenants by plan type
         */
        @Query("SELECT t.subscriptionPlan.code, COUNT(t) FROM Tenant t GROUP BY t.subscriptionPlan.code")
        List<Object[]> countByPlanType();

        /**
         * Find tenants by admin email domain
         */
        @Query("SELECT t FROM Tenant t WHERE t.adminEmail LIKE CONCAT('%@', :domain)")
        List<Tenant> findByAdminEmailDomain(@Param("domain") String domain);

        /**
         * Check if tenant code exists (case insensitive)
         */
        @Query("SELECT COUNT(t) > 0 FROM Tenant t WHERE LOWER(t.tenantCode) = LOWER(:tenantCode)")
        boolean existsByTenantCodeIgnoreCase(@Param("tenantCode") String tenantCode);
        @Query("SELECT t FROM Tenant t WHERE t.dataStatus = :dataStatus AND t.expiredAt IS NOT NULL AND :dateTime >= t.expiredAt AND t.archiveWarningNotifiedAt IS NULL")
        Page<Tenant> findTenantsEligibleForArchiveWarning(
                @Param("dataStatus") TenantDataStatus dataStatus,
                @Param("dateTime") LocalDateTime dateTime,
                Pageable pageable);

        @Query("SELECT t FROM Tenant t WHERE t.dataStatus = :dataStatus AND t.expiredAt IS NOT NULL AND :dateTime >= t.expiredAt")
        Page<Tenant> findTenantsEligibleForArchive(
                @Param("dataStatus") TenantDataStatus dataStatus,
                @Param("dateTime") LocalDateTime dateTime,
                Pageable pageable);

        @Query("SELECT t FROM Tenant t WHERE t.dataStatus = :dataStatus AND t.expiredAt IS NOT NULL AND :dateTime >= t.expiredAt AND t.finalReminderSentAt IS NULL")
        Page<Tenant> findTenantsEligibleForFinalReminder(
                @Param("dataStatus") TenantDataStatus dataStatus,
                @Param("dateTime") LocalDateTime dateTime,
                Pageable pageable);

        @Query("SELECT t FROM Tenant t WHERE t.dataStatus = :dataStatus AND t.expiredAt IS NOT NULL AND :dateTime >= t.expiredAt")
        Page<Tenant> findTenantsEligibleForSoftDelete(
                @Param("dataStatus") TenantDataStatus dataStatus,
                @Param("dateTime") LocalDateTime dateTime,
                Pageable pageable);
}