package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.EmployeeSalaryProfileHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeSalaryProfileHistoryRepository extends JpaRepository<EmployeeSalaryProfileHistory, UUID> {

    @Query("SELECT h FROM EmployeeSalaryProfileHistory h WHERE h.employeeId = :employeeId " +
            "ORDER BY h.changedAt DESC")
    List<EmployeeSalaryProfileHistory> findByEmployeeIdOrderByChangedAtDesc(@Param("employeeId") Long employeeId);

    @Query("SELECT h FROM EmployeeSalaryProfileHistory h WHERE h.profileId = :profileId " +
            "ORDER BY h.version DESC")
    List<EmployeeSalaryProfileHistory> findByProfileIdOrderByVersionDesc(@Param("profileId") UUID profileId);
}