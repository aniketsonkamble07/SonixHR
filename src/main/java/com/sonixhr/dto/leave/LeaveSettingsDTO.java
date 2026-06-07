package com.sonixhr.dto.leave;

import com.sonixhr.enums.leave.WeekendConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveSettingsDTO {

    private Long tenantId;

    // Weekend settings
    private WeekendConfig weekendConfig;
    private String customWeekendDays;
    private Boolean countWeekendsAsLeave;
    private Boolean countHolidaysAsLeave;
    private String weekendDisplay;

    // Leave balance settings
    private Integer casualLeavePerYear;
    private Integer sickLeavePerYear;
    private Integer earnedLeavePerYear;
    private Integer maxConsecutiveLeaveDays;

    // Approval settings
    private Boolean leaveApprovalRequired;
    private Boolean autoApproveForManager;

    // Holiday settings
    private String country;
    private String state;
    private Boolean includeNationalHolidays;
    private Boolean includeStateHolidays;

    // UI display fields
    private String[] workingDays;
    private String[] weekendDays;
    private String timezone;
}