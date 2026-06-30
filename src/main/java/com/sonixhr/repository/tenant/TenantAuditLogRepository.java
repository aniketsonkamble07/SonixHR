package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, Long> {
}
