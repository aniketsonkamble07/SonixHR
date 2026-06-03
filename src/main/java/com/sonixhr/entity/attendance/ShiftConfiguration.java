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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shift_configurations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "shift_code"}),
        indexes = {
                @Index(name = "idx_shift_tenant_active", columnList = "tenant_id, is_active"),
                @Index(name = "idx_shift_effective_dates", columnList = "effective_from, effective_to"),
                @Index(name = "idx_shift_time_range", columnList = "start_time, end_time")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // TENANT & IDENTIFICATION
    // =====================================================

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "shift_name", nullable = false)
    private String shiftName;

    @Column(name = "shift_code", unique = true)
    private String shiftCode;

    @Column(name = "shift_description", length = 500)
    private String shiftDescription;

    // =====================================================
    // SHIFT TIMINGS
    // =====================================================

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_duration_minutes")
    @Builder.Default
    private Integer breakDurationMinutes = 60;

    @Column(name = "min_break_minutes")
    @Builder.Default
    private Integer minBreakMinutes = 30;

    @Column(name = "max_break_minutes")
    @Builder.Default
    private Integer maxBreakMinutes = 90;

    // =====================================================
    // GRACE PERIODS
    // =====================================================

    @Column(name = "late_grace_minutes")
    @Builder.Default
    private Integer lateGraceMinutes = 15;

    @Column(name = "early_exit_grace_minutes")
    @Builder.Default
    private Integer earlyExitGraceMinutes = 15;

    @Column(name = "checkin_buffer_before")
    @Builder.Default
    private Integer checkinBufferBefore = 60;

    @Column(name = "checkout_buffer_after")
    @Builder.Default
    private Integer checkoutBufferAfter = 60;

    // =====================================================
    // WORKING HOURS THRESHOLDS (ADD THESE - THEY WERE MISSING!)
    // =====================================================

    @Column(name = "full_day_hours")
    @Builder.Default
    private Double fullDayHours = 8.0;

    @Column(name = "half_day_hours")
    @Builder.Default
    private Double halfDayHours = 4.0;

    @Column(name = "quarter_day_hours")
    @Builder.Default
    private Double quarterDayHours = 2.0;

    @Column(name = "total_hours")
    private Double totalHours;

    // =====================================================
    // OVERTIME CONFIGURATION
    // =====================================================

    @Column(name = "allow_overtime")
    @Builder.Default
    private Boolean allowOvertime = true;

    @Column(name = "overtime_multiplier")
    @Builder.Default
    private Double overtimeMultiplier = 1.5;

    @Column(name = "overtime_threshold_minutes")
    @Builder.Default
    private Integer overtimeThresholdMinutes = 30;

    @Column(name = "max_overtime_hours_per_day")
    @Builder.Default
    private Double maxOvertimeHoursPerDay = 4.0;

    // =====================================================
    // RESTRICTIONS
    // =====================================================

    @Column(name = "allow_remote_checkin")
    @Builder.Default
    private Boolean allowRemoteCheckin = true;

    @Column(name = "checkin_radius_meters")
    @Builder.Default
    private Integer checkinRadiusMeters = 100;

    // ✅ FIXED: Removed precision and scale from Double fields
    @Column(name = "office_latitude")
    private Double officeLatitude;

    @Column(name = "office_longitude")
    private Double officeLongitude;

    @Column(name = "office_address", length = 500)
    private String officeAddress;

    @Column(name = "requires_photo_evidence")
    @Builder.Default
    private Boolean requiresPhotoEvidence = false;

    // =====================================================
    // WEEKLY OFF CONFIGURATION
    // =====================================================

    @Column(name = "weekly_offs")
    private String weeklyOffs;

    @Column(name = "alternate_week_off")
    @Builder.Default
    private Boolean alternateWeekOff = false;

    // =====================================================
    // EFFECTIVE DATES
    // =====================================================

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

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

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public boolean isNightShift() {
        return endTime != null && startTime != null && endTime.isBefore(startTime);
    }

    public double getShiftDurationHours() {
        if (startTime == null || endTime == null) return 0;

        if (isNightShift()) {
            double hours1 = (24 - startTime.getHour()) + (startTime.getMinute() / 60.0);
            double hours2 = endTime.getHour() + (endTime.getMinute() / 60.0);
            return Math.round((hours1 + hours2) * 100.0) / 100.0;
        } else {
            double hours = (endTime.getHour() - startTime.getHour()) +
                    (endTime.getMinute() - startTime.getMinute()) / 60.0;
            return Math.round(hours * 100.0) / 100.0;
        }
    }

    public boolean isWithinShiftHours(LocalTime time) {
        if (time == null || startTime == null || endTime == null) return false;

        if (isNightShift()) {
            return time.isAfter(startTime) || time.isBefore(endTime);
        } else {
            return time.isAfter(startTime) && time.isBefore(endTime);
        }
    }

    public boolean isWithinCheckinBuffer(LocalTime checkinTime) {
        if (checkinTime == null || startTime == null) return false;
        LocalTime earliestCheckin = startTime.minusMinutes(checkinBufferBefore);
        return !checkinTime.isBefore(earliestCheckin);
    }

    public LocalTime getLateThreshold() {
        if (startTime == null) return null;
        return startTime.plusMinutes(lateGraceMinutes);
    }

    public LocalTime getEarlyExitThreshold() {
        if (endTime == null) return null;
        return endTime.minusMinutes(earlyExitGraceMinutes);
    }

    public LocalTime getEarliestCheckin() {
        if (startTime == null) return null;
        return startTime.minusMinutes(checkinBufferBefore);
    }

    public LocalTime getLatestCheckout() {
        if (endTime == null) return null;
        return endTime.plusMinutes(checkoutBufferAfter);
    }

    public List<String> getWeeklyOffsList() {
        if (weeklyOffs == null || weeklyOffs.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(weeklyOffs.split(","));
    }

    public void setWeeklyOffsList(List<String> weeklyOffsList) {
        if (weeklyOffsList == null || weeklyOffsList.isEmpty()) {
            this.weeklyOffs = null;
        } else {
            this.weeklyOffs = String.join(",", weeklyOffsList);
        }
    }
}