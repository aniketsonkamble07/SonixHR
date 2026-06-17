package com.sonixhr.entity.leave;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.LeaveType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_employee_date", columnList = "employee_id, start_date, end_date"),
                @Index(name = "idx_leave_status", columnList = "status"),
                @Index(name = "idx_leave_tenant", columnList = "tenant_id"),
                @Index(name = "idx_leave_dates", columnList = "start_date, end_date")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days")
    private Double totalDays;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_name", length = 100)
    private String approvedByName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_half_day")
    @Builder.Default
    private Boolean isHalfDay = false;
}