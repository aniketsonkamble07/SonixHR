package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.FnfSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FnfSettlementRepository extends JpaRepository<FnfSettlement, UUID> {
    Optional<FnfSettlement> findByEmployeeIdAndTenantId(Long employeeId, Long tenantId);
}
