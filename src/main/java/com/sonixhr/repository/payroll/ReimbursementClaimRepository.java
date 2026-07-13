package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.ReimbursementClaim;
import com.sonixhr.enums.payroll.ReimbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReimbursementClaimRepository extends JpaRepository<ReimbursementClaim, UUID> {

    @Query("SELECT r FROM ReimbursementClaim r WHERE r.tenant.id = :tenantId AND r.employee.id = :employeeId " +
           "AND r.targetMonth = :month AND r.targetYear = :year AND r.status = :status")
    List<ReimbursementClaim> findApprovedByEmployeeAndMonth(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("status") ReimbursementStatus status);
}
