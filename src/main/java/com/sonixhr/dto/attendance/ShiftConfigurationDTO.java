// ShiftConfigurationDTO.java
package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConfigurationDTO {

    // Basic Identifiers
    private Long id;
    private Long tenantId;
    private String shiftName;
    private String shiftCode;
    private String shiftDescription;

    // Shift Timings
    private LocalTime startTime;
    private LocalTime endTime;
    private Double totalHours;

    // Break Settings
    private Integer breakDurationMinutes;
    private Integer minBreakMinutes;
    private Integer maxBreakMinutes;

    // Grace Periods
    private Integer lateGraceMinutes;
    private Integer earlyExitGraceMinutes;
    private Integer checkinBufferBefore;
    private Integer checkoutBufferAfter;

    // Working Hours Thresholds
    private Double fullDayHours;
    private Double halfDayHours;
    private Double quarterDayHours;

    // Overtime Settings
    private Boolean allowOvertime;
    private Double overtimeMultiplier;
    private Integer overtimeThresholdMinutes;
    private Double maxOvertimeHoursPerDay;

    // Weekly Off Configuration
    private String weeklyOffs;  // Stored as comma-separated string
    private Boolean alternateWeekOff;

    // Effective Dates
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
    private Boolean isDefault;

    // Audit Fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    // =====================================================
    // HELPER METHODS FOR LIST CONVERSION
    // =====================================================

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

    // =====================================================
    // DISPLAY HELPER METHODS
    // =====================================================

    public String getShiftTimeFormatted() {
        if (startTime == null || endTime == null) {
            return "Not configured";
        }
        return String.format("%s - %s", startTime, endTime);
    }

    public String getShiftDurationFormatted() {
        if (totalHours == null) return "0h";
        int hours = totalHours.intValue();
        int minutes = (int) ((totalHours - hours) * 60);
        if (minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dh", hours);
    }

    public String getBreakDurationFormatted() {
        if (breakDurationMinutes == null) return "Not set";
        int hours = breakDurationMinutes / 60;
        int minutes = breakDurationMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else {
            return String.format("%dm", minutes);
        }
    }

    public String getWeeklyOffsDisplay() {
        List<String> offs = getWeeklyOffsList();
        if (offs.isEmpty()) {
            return "None";
        }
        if (alternateWeekOff != null && alternateWeekOff) {
            return String.join(", ", offs) + " (Alternate Week)";
        }
        return String.join(", ", offs);
    }

    public boolean isEffectivelyActive() {
        if (!Boolean.TRUE.equals(isActive)) return false;
        LocalDate today = LocalDate.now();
        if (effectiveFrom != null && today.isBefore(effectiveFrom)) return false;
        if (effectiveTo != null && today.isAfter(effectiveTo)) return false;
        return true;
    }

    public boolean isNightShift() {
        return endTime != null && startTime != null && endTime.isBefore(startTime);
    }

    public LocalTime getLateThreshold() {
        if (startTime == null) return null;
        return startTime.plusMinutes(lateGraceMinutes != null ? lateGraceMinutes : 15);
    }

    public LocalTime getEarlyExitThreshold() {
        if (endTime == null) return null;
        return endTime.minusMinutes(earlyExitGraceMinutes != null ? earlyExitGraceMinutes : 15);
    }

    public LocalTime getEarliestCheckin() {
        if (startTime == null) return null;
        return startTime.minusMinutes(checkinBufferBefore != null ? checkinBufferBefore : 60);
    }

    public LocalTime getLatestCheckout() {
        if (endTime == null) return null;
        return endTime.plusMinutes(checkoutBufferAfter != null ? checkoutBufferAfter : 60);
    }
}