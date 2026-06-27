package com.sonixhr.entity.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payslips", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payslip_payrun_employee", columnNames = {"payrun_id", "employee_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payslip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "payrun_id", nullable = false)
    private UUID payrunId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "gross_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "lop_days", nullable = false, precision = 4, scale = 2)
    private BigDecimal lopDays;

    @Column(name = "wages_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal wagesBase; // Base wages used for PF/ESI calculations after Labour Code adjustments

    @Column(name = "contribution_period_gross", precision = 12, scale = 2)
    private BigDecimal contributionPeriodGross; // Gross salary at start of ESI contribution period
}
