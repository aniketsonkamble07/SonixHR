package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.PayslipItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayslipItemRepository extends JpaRepository<PayslipItem, UUID> {

    @Query("SELECT p FROM PayslipItem p WHERE p.payslipId = :payslipId")
    List<PayslipItem> findByPayslipId(@Param("payslipId") UUID payslipId);
}
