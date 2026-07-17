package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.TenantSalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantSalaryStructureRepository extends JpaRepository<TenantSalaryStructure, UUID> {

    @Query("SELECT t FROM TenantSalaryStructure t WHERE t.tenant.id = :tenantId AND t.effectiveFrom <= :date AND (t.effectiveTo IS NULL OR t.effectiveTo >= :date) ORDER BY t.evaluationOrder ASC")
    List<TenantSalaryStructure> findActiveByTenantAndDate(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Query("SELECT t FROM TenantSalaryStructure t WHERE t.tenantPayrollConfigId = :configId ORDER BY t.evaluationOrder ASC")
    List<TenantSalaryStructure> findByTenantPayrollConfigId(@Param("configId") UUID configId);
}
