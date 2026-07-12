package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.PayslipItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayslipItemRepository extends JpaRepository<PayslipItem, UUID> {

    @Query("SELECT p FROM PayslipItem p WHERE p.payslipId = :payslipId")
    List<PayslipItem> findByPayslipId(@Param("payslipId") UUID payslipId);

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
}
