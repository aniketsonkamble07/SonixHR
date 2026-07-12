package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.TaxDeclarationSectionRule;
import com.sonixhr.enums.payroll.TaxRegime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxDeclarationSectionRuleRepository extends JpaRepository<TaxDeclarationSectionRule, UUID> {

    @Query("SELECT r.capAmount FROM TaxDeclarationSectionRule r " +
           "WHERE r.section = :section AND r.regime = :regime AND r.financialYear = :financialYear")
    Optional<BigDecimal> findCap(
            @Param("section") String section,
            @Param("regime") TaxRegime regime,
            @Param("financialYear") String financialYear);
}
