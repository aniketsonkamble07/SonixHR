package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.TenantPayrollConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantPayrollConfigRepository extends JpaRepository<TenantPayrollConfig, UUID> {

    @Query("SELECT c FROM TenantPayrollConfig c WHERE c.tenant.id = :tenantId " +
            "AND c.effectiveFrom <= :date AND (c.effectiveTo IS NULL OR c.effectiveTo >= :date) " +
            "AND c.isActive = true")
    Optional<TenantPayrollConfig> findActiveByTenantAndDate(@Param("tenantId") Long tenantId,
                                                            @Param("date") LocalDate date);

    @Query("SELECT c FROM TenantPayrollConfig c WHERE c.tenant.id = :tenantId AND c.isActive = true")
    List<TenantPayrollConfig> findActiveByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT c FROM TenantPayrollConfig c WHERE c.tenant.id = :tenantId " +
            "ORDER BY c.effectiveFrom DESC")
    List<TenantPayrollConfig> findByTenantOrderByEffectiveFromDesc(@Param("tenantId") Long tenantId);
}