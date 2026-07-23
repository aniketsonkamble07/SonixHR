package com.sonixhr.repository.common;

import com.sonixhr.entity.common.ApiHitLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ApiHitLogRepository extends JpaRepository<ApiHitLog, Long> {

    Page<ApiHitLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<ApiHitLog> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<ApiHitLog> findByRequestUri(String requestUri, Pageable pageable);

    Page<ApiHitLog> findByIpAddress(String ipAddress, Pageable pageable);

    Page<ApiHitLog> findByHitTimeBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ApiHitLog> findByHttpMethod(String httpMethod, Pageable pageable);

    long countByHitTimeAfter(LocalDateTime dateTime);

    @Modifying
    @Query("DELETE FROM ApiHitLog a WHERE a.hitTime < :cutoffDate")
    int deleteByHitTimeBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}