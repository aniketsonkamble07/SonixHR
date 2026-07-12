package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.TaxRegimeSlabConfig;
import com.sonixhr.enums.payroll.TaxRegime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRegimeSlabConfigRepository extends JpaRepository<TaxRegimeSlabConfig, UUID> {
    Optional<TaxRegimeSlabConfig> findByFinancialYearAndRegime(String financialYear, TaxRegime regime);
    List<TaxRegimeSlabConfig> findByFinancialYear(String financialYear);
}
