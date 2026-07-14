package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.TenantDeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantDeletionLogRepository extends JpaRepository<TenantDeletionLog, Long> {
    List<TenantDeletionLog> findByTenantId(Long tenantId);
    List<TenantDeletionLog> findByTenantCode(String tenantCode);
}
