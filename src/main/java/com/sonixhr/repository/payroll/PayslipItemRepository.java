package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.PayslipItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayslipItemRepository extends JpaRepository<PayslipItem, UUID> {

    @Query("SELECT p FROM PayslipItem p WHERE p.payslipId = :payslipId")
    List<PayslipItem> findByPayslipId(@Param("payslipId") UUID payslipId);

    @Query("SELECT SUM(i.amount) FROM PayslipItem i WHERE i.componentCode = 'LOAN_EMI' AND i.resolvedVariables LIKE CONCAT('%\"loanId\":\"', :loanId, '\"%')")
    BigDecimal sumRecoveredForLoan(@Param("loanId") String loanId);

    @Query("SELECT pi.amount FROM PayslipItem pi " +
           "JOIN Payslip p ON p.id = pi.payslipId " +
           "JOIN Payrun pr ON pr.id = p.payrunId " +
           "WHERE p.employee.id = :employeeId " +
           "AND p.tenant.id = :tenantId " +
           "AND pi.componentCode = 'BASIC' " +
           "AND pr.status != 'SUPERSEDED' " +
           "AND pr.processedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY pr.processedAt DESC")
    List<BigDecimal> findLastBasicSalaries(@Param("tenantId") Long tenantId,
                                          @Param("employeeId") Long employeeId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);

    @Query("SELECT SUM(pi.amount) FROM PayslipItem pi, Payslip p, Payrun pr " +
           "WHERE pi.payslipId = p.id AND p.payrunId = pr.id " +
           "AND p.tenant.id = :tenantId AND p.employee.id = :employeeId " +
           "AND pi.componentCode = 'TDS' AND pr.status <> 'SUPERSEDED' " +
           "AND (pr.year * 12 + pr.month) BETWEEN :startVal AND :endVal")
    BigDecimal sumTdsForEmployeeInFinancialYear(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("startVal") Integer startVal,
            @Param("endVal") Integer endVal);

    @Query("SELECT SUM(p.taxableGrossEarnings) FROM Payslip p " +
           "JOIN Payrun pr ON pr.id = p.payrunId " +
           "WHERE p.employee.id = :employeeId " +
           "AND p.tenant.id = :tenantId " +
           "AND pr.month + (pr.year * 12) >= :startPeriod " +
           "AND pr.month + (pr.year * 12) <= :endPeriod " +
           "AND pr.status != 'SUPERSEDED'")
    BigDecimal sumTaxableGrossForEmployeeInFinancialYear(@Param("tenantId") Long tenantId,
                                                        @Param("employeeId") Long employeeId,
                                                        @Param("startPeriod") int startPeriod,
                                                        @Param("endPeriod") int endPeriod);
}
