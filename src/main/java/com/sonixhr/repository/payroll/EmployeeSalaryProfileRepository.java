package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.EmployeeSalaryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSalaryProfileRepository extends JpaRepository<EmployeeSalaryProfile, UUID> {

    @Query("SELECT e FROM EmployeeSalaryProfile e WHERE e.employee.id = :employeeId AND e.effectiveFrom <= :date AND (e.effectiveTo IS NULL OR e.effectiveTo >= :date)")
    Optional<EmployeeSalaryProfile> findActiveByEmployeeAndDate(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeeSalaryProfile e WHERE e.tenant.id = :tenantId AND e.effectiveFrom <= :date AND (e.effectiveTo IS NULL OR e.effectiveTo >= :date)")
    List<EmployeeSalaryProfile> findActiveByTenantAndDate(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeeSalaryProfile e WHERE e.tenant.id = :tenantId AND e.effectiveFrom <= :endDate AND (e.effectiveTo IS NULL OR e.effectiveTo >= :startDate)")
    List<EmployeeSalaryProfile> findActiveProfilesByTenantInPeriod(
            @Param("tenantId") Long tenantId, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);

    List<EmployeeSalaryProfile> findByEmployeeIdOrderByEffectiveFromAsc(Long employeeId);

    List<EmployeeSalaryProfile> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    @Query("SELECT e FROM EmployeeSalaryProfile e WHERE e.employee.id = :employeeId AND e.isActive = true")
    List<EmployeeSalaryProfile> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT MAX(e.version) FROM EmployeeSalaryProfile e WHERE e.employee.id = :employeeId")
    Optional<Integer> findMaxVersionByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT e FROM EmployeeSalaryProfile e WHERE e.employee.id = :employeeId AND e.effectiveFrom <= :endDate AND (e.effectiveTo IS NULL OR e.effectiveTo >= :startDate) ORDER BY e.effectiveFrom ASC")
    List<EmployeeSalaryProfile> findProfilesOverlappingPeriod(
            @Param("employeeId") Long employeeId, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
}
