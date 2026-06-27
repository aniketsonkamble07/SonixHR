package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.EmployeeSalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSalaryComponentRepository extends JpaRepository<EmployeeSalaryComponent, UUID> {

    @Query("SELECT e FROM EmployeeSalaryComponent e WHERE e.salaryProfile.id = :salaryProfileId")
    List<EmployeeSalaryComponent> findBySalaryProfileId(@Param("salaryProfileId") UUID salaryProfileId);

    @Query("SELECT e FROM EmployeeSalaryComponent e WHERE e.salaryProfile.id = :profileId AND e.componentCode = :componentCode")
    Optional<EmployeeSalaryComponent> findByProfileAndComponent(@Param("profileId") UUID profileId, @Param("componentCode") String componentCode);
}
