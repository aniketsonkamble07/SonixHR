package com.sonixhr.dto.leave;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
    private Boolean prorated;

    public static LeavePolicyDTOBuilder builder() {
        return new LeavePolicyDTOBuilder();
    }

    public static class LeavePolicyDTOBuilder {
        private Boolean allowed;
        private Integer daysPerYear;
        private Boolean carryForward;
        private Integer maxCarryForwardDays;
        private Integer minimumServiceMonths;
        private String genderEligibility;
        private Boolean probationPeriodAllowed;
        private Boolean prorated;

        public LeavePolicyDTOBuilder allowed(Boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        public LeavePolicyDTOBuilder daysPerYear(Integer daysPerYear) {
            this.daysPerYear = daysPerYear;
            return this;
        }

        public LeavePolicyDTOBuilder carryForward(Boolean carryForward) {
            this.carryForward = carryForward;
            return this;
        }

        public LeavePolicyDTOBuilder maxCarryForwardDays(Integer maxCarryForwardDays) {
            this.maxCarryForwardDays = maxCarryForwardDays;
            return this;
        }

        public LeavePolicyDTOBuilder minimumServiceMonths(Integer minimumServiceMonths) {
            this.minimumServiceMonths = minimumServiceMonths;
            return this;
        }

        public LeavePolicyDTOBuilder genderEligibility(String genderEligibility) {
            this.genderEligibility = genderEligibility;
            return this;
        }

        public LeavePolicyDTOBuilder probationPeriodAllowed(Boolean probationPeriodAllowed) {
            this.probationPeriodAllowed = probationPeriodAllowed;
            return this;
        }

        public LeavePolicyDTOBuilder prorated(Boolean prorated) {
            this.prorated = prorated;
            return this;
        }

        public LeavePolicyDTO build() {
            LeavePolicyDTO policy = new LeavePolicyDTO();
            policy.allowed = this.allowed;
            policy.daysPerYear = this.daysPerYear;
            policy.carryForward = this.carryForward;
            policy.maxCarryForwardDays = this.maxCarryForwardDays;
            policy.minimumServiceMonths = this.minimumServiceMonths;
            policy.genderEligibility = this.genderEligibility;
            policy.probationPeriodAllowed = this.probationPeriodAllowed;
            policy.prorated = this.prorated;
            return policy;
        }
    }
}
