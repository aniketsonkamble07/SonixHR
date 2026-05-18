package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    // ===== Basic Finders =====
    Optional<Tenant> findBySubdomain(String subdomain);
    Optional<Tenant> findByTenantCode(String tenantCode);
    Optional<Tenant> findByAdminEmail(String adminEmail);

    // ===== Existence Checks =====
    boolean existsBySubdomain(String subdomain);
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
    int updateTenantStatus(@Param("tenantId") UUID tenantId,
                           @Param("status") String status,
                           @Param("isActive") Boolean isActive,
                           @Param("suspendedAt") LocalDateTime suspendedAt);

    // ===== Soft Delete Methods =====
    @Modifying
    @Transactional
    @Query("UPDATE Tenant t SET t.status = 'deleted', t.isActive = false, t.deletedAt = CURRENT_TIMESTAMP WHERE t.id = :tenantId")
    int softDeleteById(@Param("tenantId") UUID tenantId);

    @Modifying
    @Transactional
    @Query("UPDATE Tenant t SET t.status = 'active', t.isActive = true, t.deletedAt = NULL WHERE t.id = :tenantId")
    int restoreById(@Param("tenantId") UUID tenantId);

    // ===== Search with Multiple Criteria =====
    @Query("SELECT t FROM Tenant t WHERE " +
            "(:companyName IS NULL OR LOWER(t.companyName) LIKE LOWER(CONCAT('%', :companyName, '%'))) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:isActive IS NULL OR t.isActive = :isActive) AND " +
            "(:subdomain IS NULL OR LOWER(t.subdomain) LIKE LOWER(CONCAT('%', :subdomain, '%')))")
    Page<Tenant> searchTenants(@Param("companyName") String companyName,
                               @Param("status") String status,
                               @Param("isActive") Boolean isActive,
                               @Param("subdomain") String subdomain,
                               Pageable pageable);

    // ===== Count Queries =====
    long countByStatus(String status);
    long countByIsActiveTrue();

    // ===== Expiring Trials =====
    List<Tenant> findByPlanStatusAndTrialEndsAtBefore(String planStatus, LocalDateTime dateTime);
}