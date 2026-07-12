package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.TaxDeclaration;
import com.sonixhr.enums.payroll.DeclarationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxDeclarationRepository extends JpaRepository<TaxDeclaration, UUID> {
    Optional<TaxDeclaration> findByEmployeeIdAndFinancialYearAndStatus(
            Long employeeId, String financialYear, DeclarationStatus status);
}
