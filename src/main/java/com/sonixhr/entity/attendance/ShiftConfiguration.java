package com.sonixhr.entity.attendance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shift_configurations",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tenant_id", "shift_code"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "shift_code", nullable = false, length = 20)
    private String shiftCode;

    @Column(name = "shift_description", length = 500)
    private String shiftDescription;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "total_hours")
    private Double totalHours;

    @Column(name = "break_duration_minutes")
    private Integer breakDurationMinutes;

    @Column(name = "min_break_minutes")
    private Integer minBreakMinutes;

    @Column(name = "max_break_minutes")
    private Integer maxBreakMinutes;

    @Column(name = "late_grace_minutes")
    private Integer lateGraceMinutes;

    @Column(name = "early_exit_grace_minutes")
    private Integer earlyExitGraceMinutes;

    @Column(name = "checkin_buffer_before")
    private Integer checkinBufferBefore;

    @Column(name = "checkout_buffer_after")
    private Integer checkoutBufferAfter;

    @Column(name = "full_day_hours")
    private Double fullDayHours;

    @Column(name = "half_day_hours")
    private Double halfDayHours;

    @Column(name = "quarter_day_hours")
    private Double quarterDayHours;

    @Column(name = "allow_overtime")
    private Boolean allowOvertime;

    @Column(name = "overtime_multiplier")
    private Double overtimeMultiplier;

    @Column(name = "overtime_threshold_minutes")
    private Integer overtimeThresholdMinutes;

    @Column(name = "max_overtime_hours_per_day")
    private Double maxOvertimeHoursPerDay;  // Changed to Double

    @Column(name = "weekly_offs")
    private String weeklyOffs;

    @Column(name = "alternate_week_off")
    private Boolean alternateWeekOff;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}