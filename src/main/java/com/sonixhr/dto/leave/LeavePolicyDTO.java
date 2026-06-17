package com.sonixhr.dto.leave;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeavePolicyDTO {
    private Boolean allowed;
    private Integer daysPerYear;
    private Boolean carryForward;
    private Integer maxCarryForwardDays;
    private Integer minimumServiceMonths;
    private String genderEligibility;
    private Boolean probationPeriodAllowed;
    private List<String> roleEligibility;
    private Boolean prorated;
}
