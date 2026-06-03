package com.sonixhr.entity.attendance;

import com.sonixhr.enums.attendance.AttendanceApprovalStatus;
import com.sonixhr.enums.attendance.AttendanceMethod;
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
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "employee_id", "date"}),
        indexes = {
                @Index(name = "idx_attendance_method", columnList = "method"),
                @Index(name = "idx_attendance_approval", columnList = "approval_status"),
                @Index(name = "idx_attendance_device", columnList = "device_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // BASIC INFORMATION
    // =====================================================

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", length = 50)
    private String employeeCode;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // =====================================================
    // TIME TRACKING
    // =====================================================

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Column(name = "break_minutes")
    @Builder.Default
    private Integer breakMinutes = 0;

    @Column(name = "total_break_minutes")
    @Builder.Default
    private Integer totalBreakMinutes = 0;

    @Column(name = "working_hours")
    private Double workingHours;

    @Column(name = "overtime_hours")
    @Builder.Default
    private Double overtimeHours = 0.0;

    @Column(name = "shortage_hours")
    @Builder.Default
    private Double shortageHours = 0.0;

    @Column(name = "late_minutes")
    @Builder.Default
    private Integer lateMinutes = 0;

    @Column(name = "early_exit_minutes")
    @Builder.Default
    private Integer earlyExitMinutes = 0;

    // =====================================================
    // STATUS FIELDS
    // =====================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private AttendanceMethod method;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // =====================================================
    // BIOMETRIC DEVICE FIELDS
    // =====================================================

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_serial_number")
    private String deviceSerialNumber;

    @Column(name = "biometric_user_id")
    private String biometricUserId;

    @Column(name = "verification_mode")
    private String verificationMode;

    @Column(name = "raw_device_data")
    private String rawDeviceData;

    @Column(name = "is_auto_synced")
    @Builder.Default
    private Boolean isAutoSynced = false;

    @Column(name = "device_synced_at")
    private LocalDateTime deviceSyncedAt;

    // =====================================================
    // APPROVAL WORKFLOW (For Self & Manual methods)
    // =====================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private AttendanceApprovalStatus approvalStatus = AttendanceApprovalStatus.NOT_REQUIRED;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_name")
    private String approvedByName;

    @Column(name = "approved_by_role")
    private String approvedByRole;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // =====================================================
    // REQUESTOR FIELDS (Who created/updated this record)
    // =====================================================

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @Column(name = "requested_by_role")
    private String requestedByRole;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    // =====================================================
    // CORRECTION HISTORY (For manual edits)
    // =====================================================

    @Column(name = "original_check_in")
    private LocalTime originalCheckIn;

    @Column(name = "original_check_out")
    private LocalTime originalCheckOut;

    @Column(name = "correction_reason")
    private String correctionReason;

    @Column(name = "corrected_by")
    private Long correctedBy;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    @Column(name = "correction_count")
    @Builder.Default
    private Integer correctionCount = 0;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // SOFT DELETE
    // =====================================================

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    // =====================================================
    // VERSION FOR OPTIMISTIC LOCKING
    // =====================================================

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean isBiometric() {
        return this.method == AttendanceMethod.BIOMETRIC;
    }

    public boolean isSelfMarked() {
        return this.method == AttendanceMethod.SELF;
    }

    public boolean isManual() {
        return this.method == AttendanceMethod.MANUAL;
    }

    public boolean requiresApproval() {
        return this.method == AttendanceMethod.SELF || this.method == AttendanceMethod.MANUAL;
    }

    public boolean isApproved() {
        return this.approvalStatus == AttendanceApprovalStatus.APPROVED ||
                this.approvalStatus == AttendanceApprovalStatus.NOT_REQUIRED;
    }

    public boolean isPending() {
        return this.approvalStatus == AttendanceApprovalStatus.PENDING;
    }
}