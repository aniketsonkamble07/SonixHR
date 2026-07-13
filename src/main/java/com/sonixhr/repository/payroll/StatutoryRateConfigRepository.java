package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.StatutoryRateConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface StatutoryRateConfigRepository extends JpaRepository<StatutoryRateConfig, UUID> {

    @Query("SELECT s FROM StatutoryRateConfig s WHERE s.isDeleted = false AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)")
    List<StatutoryRateConfig> findActiveByDate(@Param("date") LocalDate date);

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);

    List<StatutoryRateConfig> findAllByIsDeletedFalse();
}

