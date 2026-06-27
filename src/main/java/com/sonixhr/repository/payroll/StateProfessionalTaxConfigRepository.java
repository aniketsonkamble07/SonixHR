package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.enums.IndianState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface StateProfessionalTaxConfigRepository extends JpaRepository<StateProfessionalTaxConfig, UUID> {

    @Query("SELECT s FROM StateProfessionalTaxConfig s WHERE s.stateCode = :stateCode AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)")
    List<StateProfessionalTaxConfig> findActiveByStateAndDate(@Param("stateCode") IndianState stateCode, @Param("date") LocalDate date);

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);

    boolean existsByStateCode(IndianState stateCode);
}
