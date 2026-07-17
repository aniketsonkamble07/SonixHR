package com.sonixhr.repository.common;

import com.sonixhr.entity.common.ApiHitLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiHitLogRepository extends JpaRepository<ApiHitLog, Long> {

    /**
     * Get paginated API hit logs for a specific tenant
     */
    Page<ApiHitLog> findByTenantId(Long tenantId, Pageable pageable);
}
