package com.sonixhr.entity.payroll;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_salary_profile_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSalaryProfileHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "monthly_ctc", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyCtc;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Lob
    @Column(name = "component_snapshot", columnDefinition = "TEXT")
    private String componentSnapshot; // JSON of all components at that time
}