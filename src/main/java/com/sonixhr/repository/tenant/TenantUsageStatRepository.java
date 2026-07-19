package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantUsageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantUsageStatRepository extends JpaRepository<TenantUsageStat, Long> {

    @Query("SELECT s FROM TenantUsageStat s WHERE s.tenant.id = :tenantId ORDER BY s.statDate DESC, s.id DESC")
    List<TenantUsageStat> findLatestByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT s FROM TenantUsageStat s WHERE s.statDate = (SELECT MAX(sub.statDate) FROM TenantUsageStat sub WHERE sub.tenant.id = s.tenant.id) AND s.tenant.deletedAt IS NULL")
    List<TenantUsageStat> findAllLatestStats();
}
