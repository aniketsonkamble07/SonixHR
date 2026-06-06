package com.sonixhr.entity.attendance;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.attendance.AttendanceApprovalStatus;
import com.sonixhr.enums.attendance.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_attendance_date",
                        columnNames = {"employee_id", "attendance_date"})
        },
        indexes = {
                @Index(name = "idx_attendance_employee_date", columnList = "employee_id, attendance_date"),
                @Index(name = "idx_attendance_marked_by_manager", columnList = "marked_by_manager_id, attendance_date"),
                @Index(name = "idx_attendance_tenant", columnList = "tenant_id"),
                @Index(name = "idx_attendance_approval", columnList = "approval_status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Employee whose attendance is being marked
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Manager who marked this attendance (can be null if marked by Super Admin)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_manager_id")
    private Employee markedByManager;

    // Super Admin who marked this (if applicable)
    @Column(name = "marked_by_admin_id")
    private Long markedByAdminId;

    @Column(name = "marked_by_admin_name")
    private String markedByAdminName;

    // Attendance details
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(name = "total_working_hours")
    private Double totalWorkingHours;

    // Reason for late or absence
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private AttendanceStatus attendanceStatus;  // PRESENT, ABSENT, LATE, HALF_DAY, ON_LEAVE

    // Approval (if workflow needed)
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    @Builder.Default
    private AttendanceApprovalStatus approvalStatus = AttendanceApprovalStatus.APPROVED;

    // ✅ ADD THESE MISSING FIELDS
    @Column(name = "approved_by")
    private Long approvedBy;  // ID of the person who approved

    @Column(name = "approved_by_name")
    private String approvedByName;  // Name of the person who approved

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;  // When it was approved

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;  // Reason for rejection

    // Audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // Helper methods
    public Long getTenantId() {
        return tenant != null ? tenant.getId() : null;
    }

    public Long getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    public String getEmployeeName() {
        return employee != null ? employee.getFullName() : null;
    }

    public String getEmployeeCode() {
        return employee != null ? employee.getEmployeeCode() : null;
    }

    public Long getManagerId() {
        return employee != null && employee.getManager() != null ? employee.getManager().getId() : null;
    }
}