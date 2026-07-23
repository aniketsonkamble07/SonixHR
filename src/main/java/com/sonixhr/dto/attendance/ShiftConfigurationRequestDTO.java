package com.sonixhr.dto.attendance;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConfigurationRequestDTO {

    @NotNull(message = "Shift name is required")
    private String shiftName;

    private String shiftCode;
    private String shiftDescription;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @Min(value = 0, message = "Break duration must be positive")
    private Integer breakDurationMinutes;

    @Min(value = 0, message = "Minimum break must be positive")
    private Integer minBreakMinutes;

    @Min(value = 0, message = "Maximum break must be positive")
    private Integer maxBreakMinutes;

    @Min(value = 0, message = "Late grace minutes must be positive")
    private Integer lateGraceMinutes;

    @Min(value = 0, message = "Early exit grace minutes must be positive")
    private Integer earlyExitGraceMinutes;

    @Min(value = 0, message = "Check-in buffer must be positive")
    private Integer checkinBufferBefore;

    @Min(value = 0, message = "Check-out buffer must be positive")
    private Integer checkoutBufferAfter;

    @Min(value = 0, message = "Full day hours must be positive")
    private Double fullDayHours;

    @Min(value = 0, message = "Half day hours must be positive")
    private Double halfDayHours;

    @Min(value = 0, message = "Quarter day hours must be positive")
    private Double quarterDayHours;

    private Boolean allowOvertime;

    @Min(value = 1, message = "Overtime multiplier must be at least 1")
    private Double overtimeMultiplier;

    @Min(value = 0, message = "Overtime threshold must be positive")
    private Integer overtimeThresholdMinutes;

    @Min(value = 0, message = "Max overtime hours must be positive")
    private Double maxOvertimeHoursPerDay;

    /**
     * Stored internally as a comma-separated string, e.g. "SATURDAY,SUNDAY".
     * Accepts either a JSON array (["SATURDAY","SUNDAY"]) or a plain string
     * ("SATURDAY,SUNDAY") from the client — see setWeeklyOffs(Object) below.
     */
    private String weeklyOffs;

    private Boolean alternateWeekOff;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @JsonSetter("weeklyOffs")
    public void setWeeklyOffs(Object value) {
        if (value == null) {
            this.weeklyOffs = null;
        } else if (value instanceof Collection<?> collection) {
            this.weeklyOffs = collection.isEmpty()
                    ? null
                    : collection.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
        } else {
            String text = value.toString().trim();
            this.weeklyOffs = text.isEmpty() ? null : text;
        }
    }

    @AssertTrue(message = "Start time cannot be equal to end time")
    private boolean isStartTimeNotEqualToEndTime() {
        return startTime == null || endTime == null || !startTime.equals(endTime);
    }

    @AssertTrue(message = "Half day hours must be less than or equal to full day hours")
    private boolean isHalfDayLessThanFullDay() {
        return fullDayHours == null || halfDayHours == null || halfDayHours <= fullDayHours;
    }

    @AssertTrue(message = "Quarter day hours must be less than or equal to half day hours")
    private boolean isQuarterDayLessThanHalfDay() {
        return halfDayHours == null || quarterDayHours == null || quarterDayHours <= halfDayHours;
    }

    @AssertTrue(message = "Minimum break cannot exceed maximum break")
    private boolean isMinBreakLessThanMaxBreak() {
        return minBreakMinutes == null || maxBreakMinutes == null || minBreakMinutes <= maxBreakMinutes;
    }

    @AssertTrue(message = "Break duration must be between min and max break")
    private boolean isBreakDurationWithinRange() {
        if (breakDurationMinutes == null) return true;
        if (minBreakMinutes != null && breakDurationMinutes < minBreakMinutes) return false;
        if (maxBreakMinutes != null && breakDurationMinutes > maxBreakMinutes) return false;
        return true;
    }

    @AssertTrue(message = "Effective from date cannot be after effective to date")
    private boolean isValidEffectiveDates() {
        return effectiveFrom == null || effectiveTo == null || !effectiveFrom.isAfter(effectiveTo);
    }
}