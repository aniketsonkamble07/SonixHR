package com.sonixhr.entity.attendance;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
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

@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_employee_date_tenant",
                columnNames = {"tenant_id", "employee_id", "attendance_date"}
        ),
        indexes = {
                @Index(name = "idx_attendance_tenant_date", columnList = "tenant_id, attendance_date"),
                @Index(name = "idx_attendance_employee", columnList = "employee_id"),
                @Index(name = "idx_attendance_date", columnList = "attendance_date"),
                @Index(name = "idx_attendance_status", columnList = "status")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "overtime_hours")
    private Double overtimeHours;

    @Column(length = 500)
    private String reason;

    // Who marked this attendance
    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "marked_by_name", length = 100)
    private String markedByName;

    @Column(name = "marked_by_role", length = 20)
    private String markedByRole; // MANAGER, SUPER_ADMIN, ADMIN

    @CreationTimestamp
    @Column(name = "marked_at", updatable = false)
    private LocalDateTime markedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}