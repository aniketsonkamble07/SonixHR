package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.SalaryComponentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalaryComponentDefinitionRepository extends JpaRepository<SalaryComponentDefinition, UUID> {

    @Query("SELECT s FROM SalaryComponentDefinition s WHERE s.tenant.id = :tenantId " +
            "AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date) " +
            "AND s.isActive = true " +
            "ORDER BY s.evaluationOrder ASC")
    List<SalaryComponentDefinition> findActiveByTenantAndDate(@Param("tenantId") Long tenantId,
                                                              @Param("date") LocalDate date);

    @Query("SELECT s FROM SalaryComponentDefinition s WHERE s.tenant.id = :tenantId " +
            "AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date) " +
            "AND s.isActive = true AND s.isAllowedByTenant = true " +
            "ORDER BY s.evaluationOrder ASC")
    List<SalaryComponentDefinition> findAllowedByTenantAndDate(@Param("tenantId") Long tenantId,
                                                               @Param("date") LocalDate date);

    @Query("SELECT s FROM SalaryComponentDefinition s WHERE s.tenant.id = :tenantId " +
            "AND s.componentCode = :componentCode " +
            "AND s.isActive = true")
    Optional<SalaryComponentDefinition> findByTenantAndComponentCode(@Param("tenantId") Long tenantId,
                                                                     @Param("componentCode") String componentCode);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SalaryComponentDefinition s " +
            "WHERE s.tenant.id = :tenantId AND s.componentCode = :componentCode AND s.isActive = true")
    boolean existsByTenantIdAndComponentCode(@Param("tenantId") Long tenantId,
                                             @Param("componentCode") String componentCode);

    List<SalaryComponentDefinition> findByTenantPayrollConfigId(UUID tenantPayrollConfigId);
}