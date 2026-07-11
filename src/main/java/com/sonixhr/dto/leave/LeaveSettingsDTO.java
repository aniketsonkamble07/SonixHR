package com.sonixhr.dto.leave;

import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.leave.WeekendConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LeaveSettingsDTO {

    private java.util.Map<String, LeavePolicyDTO> leavePolicies;

    private Boolean policiesConfigured;

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
    private Integer emergencyLeavePerYear;
    private Integer maternityLeavePerYear;
    private Integer paternityLeavePerYear;
    private Integer unpaidLeavePerYear;
    private Integer compensatoryLeavePerYear;
    private Integer maxConsecutiveLeaveDays;

    // Approval settings
    private Boolean leaveApprovalRequired;
    private Boolean autoApproveForManager;

    // Holiday settings
    private String country;
    private IndianState state;
    private String stateText;
    private Boolean includeNationalHolidays;
    private Boolean includeStateHolidays;

    // UI display fields
    private String[] workingDays;
    private String[] weekendDays;
    private String timezone;
}