package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, Long> {

    @Query("SELECT a FROM TenantAuditLog a WHERE a.tenant.id = :tenantId ORDER BY a.createdAt DESC")
    Page<TenantAuditLog> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT a FROM TenantAuditLog a WHERE a.tenant.id = :tenantId AND a.action = :action ORDER BY a.createdAt DESC")
    Page<TenantAuditLog> findByTenantIdAndActionOrderByCreatedAtDesc(@Param("tenantId") Long tenantId,
                                                                     @Param("action") String action,
                                                                     Pageable pageable);
}
