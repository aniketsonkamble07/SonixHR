package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.Payslip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    @Query("SELECT p FROM Payslip p WHERE p.payrunId = :payrunId AND p.employee.id = :employeeId")
    Optional<Payslip> findByPayrunIdAndEmployeeId(@Param("payrunId") UUID payrunId, @Param("employeeId") Long employeeId);

    @Query("SELECT p FROM Payslip p WHERE p.payrunId = :payrunId")
    List<Payslip> findByPayrunId(@Param("payrunId") UUID payrunId);

    @Query("SELECT p FROM Payslip p, Payrun pr WHERE p.payrunId = pr.id AND p.tenant.id = :tenantId AND p.employee.id = :employeeId AND pr.status <> 'SUPERSEDED' ORDER BY pr.year DESC, pr.month DESC")
    List<Payslip> findByEmployeeId(@Param("tenantId") Long tenantId, @Param("employeeId") Long employeeId);

    @Query("SELECT p FROM Payslip p, Payrun pr WHERE p.payrunId = pr.id AND p.tenant.id = :tenantId AND pr.month = :month AND pr.year = :year AND pr.status <> 'SUPERSEDED'")
    List<Payslip> findByTenantAndMonthAndYear(
            @Param("tenantId") Long tenantId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    @Query(value = "SELECT p FROM Payslip p, Payrun pr WHERE p.payrunId = pr.id AND p.tenant.id = :tenantId AND pr.month = :month AND pr.year = :year AND pr.status <> 'SUPERSEDED'",
           countQuery = "SELECT COUNT(p) FROM Payslip p, Payrun pr WHERE p.payrunId = pr.id AND p.tenant.id = :tenantId AND pr.month = :month AND pr.year = :year AND pr.status <> 'SUPERSEDED'")
    Page<Payslip> findByTenantAndMonthAndYearPaged(
            @Param("tenantId") Long tenantId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable);

    @Query("SELECT SUM(p.taxableGrossEarnings) FROM Payslip p, Payrun pr " +
           "WHERE p.payrunId = pr.id AND p.tenant.id = :tenantId AND p.employee.id = :employeeId " +
           "AND pr.status <> 'SUPERSEDED' " +
           "AND (pr.year * 12 + pr.month) BETWEEN :startVal AND :endVal")
    BigDecimal sumTaxableGrossForEmployeeInFinancialYear(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("startVal") Integer startVal,
            @Param("endVal") Integer endVal);
}
