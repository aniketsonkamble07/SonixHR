package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.Payrun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrunRepository extends JpaRepository<Payrun, UUID> {

    @Query("SELECT p FROM Payrun p WHERE p.tenant.id = :tenantId AND p.month = :month AND p.year = :year AND p.status <> 'SUPERSEDED'")
    Optional<Payrun> findByTenantAndMonthAndYear(@Param("tenantId") Long tenantId, @Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT COALESCE(MAX(p.version), 0) FROM Payrun p WHERE p.tenant.id = :tenantId AND p.month = :month AND p.year = :year")
    Integer findLatestVersionNumber(@Param("tenantId") Long tenantId, @Param("month") Integer month, @Param("year") Integer year);
}
