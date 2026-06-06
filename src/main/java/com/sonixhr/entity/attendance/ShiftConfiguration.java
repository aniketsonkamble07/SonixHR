package com.sonixhr.entity.attendance;

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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shift_configurations")
public class ShiftConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "shift_code", length = 50)
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
    private Double maxOvertimeHoursPerDay;

    @Column(name = "weekly_offs", length = 100)
    private String weeklyOffs;

    @Column(name = "alternate_week_off")
    private Boolean alternateWeekOff;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_default")
    private Boolean isDefault;

    // ✅ Add this field
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public LocalTime getLateThreshold() {
        if (lateGraceMinutes != null && lateGraceMinutes > 0) {
            return startTime.plusMinutes(lateGraceMinutes);
        }
        return startTime;
    }

    public LocalTime getEarlyExitThreshold() {
        if (earlyExitGraceMinutes != null && earlyExitGraceMinutes > 0) {
            return endTime.minusMinutes(earlyExitGraceMinutes);
        }
        return endTime;
    }

    public LocalTime getEarliestCheckin() {
        if (checkinBufferBefore != null && checkinBufferBefore > 0) {
            return startTime.minusMinutes(checkinBufferBefore);
        }
        return startTime;
    }

    public LocalTime getLatestCheckout() {
        if (checkoutBufferAfter != null && checkoutBufferAfter > 0) {
            return endTime.plusMinutes(checkoutBufferAfter);
        }
        return endTime;
    }
}