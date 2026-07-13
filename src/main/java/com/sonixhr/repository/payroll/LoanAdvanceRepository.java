package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.LoanAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanAdvanceRepository extends JpaRepository<LoanAdvance, UUID> {

    @Query("SELECT l FROM LoanAdvance l WHERE l.employee.id = :employeeId AND l.tenant.id = :tenantId AND l.status = 'ACTIVE'")
    List<LoanAdvance> findActiveByEmployeeIdAndTenantId(@Param("employeeId") Long employeeId, @Param("tenantId") Long tenantId);
}
