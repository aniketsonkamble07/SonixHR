package com.sonixhr.entity.leave;

import com.sonixhr.enums.leave.WeekendConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tenant_leave_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantLeaveSettings {

    @Id
    private Long tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leave_policies", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> leavePolicies = createDefaultPolicies();

    public static Map<String, Object> createDefaultPolicies() {
        Map<String, Object> policies = new HashMap<>();
        
        // CASUAL
        policies.put("CASUAL", Map.of(
                "allowed", true,
                "daysPerYear", 12,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));
        
        // SICK
        policies.put("SICK", Map.of(
                "allowed", true,
                "daysPerYear", 12,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));
        
        // EARNED
        policies.put("EARNED", Map.of(
                "allowed", true,
                "daysPerYear", 15,
                "carryForward", true,
                "maxCarryForwardDays", 30,
                "minimumServiceMonths", 6,
                "genderEligibility", "ALL"
        ));

        // EMERGENCY
        policies.put("EMERGENCY", Map.of(
                "allowed", true,
                "daysPerYear", 3,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));

        // MATERNITY
        policies.put("MATERNITY", Map.of(
                "allowed", true,
                "daysPerYear", 84,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "FEMALE"
        ));

        // PATERNITY
        policies.put("PATERNITY", Map.of(
                "allowed", true,
                "daysPerYear", 5,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "MALE"
        ));

        // UNPAID
        policies.put("UNPAID", Map.of(
                "allowed", true,
                "daysPerYear", 0,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));

        // COMPENSATORY
        policies.put("COMPENSATORY", Map.of(
                "allowed", true,
                "daysPerYear", 0,
                "carryForward", false,
                "maxCarryForwardDays", 0,
                "minimumServiceMonths", 0,
                "genderEligibility", "ALL"
        ));
        
        return policies;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "weekend_config")
    @Builder.Default
    private WeekendConfig weekendConfig = WeekendConfig.SATURDAY_SUNDAY;

    @Column(name = "custom_weekend_days")
    @Builder.Default
    private String customWeekendDays = null;

    @Column(name = "count_weekends_as_leave")
    @Builder.Default
    private Boolean countWeekendsAsLeave = false;

    @Column(name = "count_holidays_as_leave")
    @Builder.Default
    private Boolean countHolidaysAsLeave = false;

    @Column(name = "policies_configured")
    @Builder.Default
    private Boolean policiesConfigured = false;

    @Column(name = "casual_leave_per_year")
    @Builder.Default
    private Integer casualLeavePerYear = 12;

    @Column(name = "sick_leave_per_year")
    @Builder.Default
    private Integer sickLeavePerYear = 12;

    @Column(name = "earned_leave_per_year")
    @Builder.Default
    private Integer earnedLeavePerYear = 15;

    @Column(name = "emergency_leave_per_year")
    @Builder.Default
    private Integer emergencyLeavePerYear = 3;

    @Column(name = "maternity_leave_per_year")
    @Builder.Default
    private Integer maternityLeavePerYear = 84;

    @Column(name = "paternity_leave_per_year")
    @Builder.Default
    private Integer paternityLeavePerYear = 5;

    @Column(name = "unpaid_leave_per_year")
    @Builder.Default
    private Integer unpaidLeavePerYear = 0;

    @Column(name = "compensatory_leave_per_year")
    @Builder.Default
    private Integer compensatoryLeavePerYear = 0;

    @Column(name = "max_consecutive_leave_days")
    @Builder.Default
    private Integer maxConsecutiveLeaveDays = 30;

    @Column(name = "leave_approval_required")
    @Builder.Default
    private Boolean leaveApprovalRequired = true;

    @Column(name = "auto_approve_for_manager")
    @Builder.Default
    private Boolean autoApproveForManager = true;

    @Column(length = 50)
    @Builder.Default
    private String country = null;

    @Column(length = 50)
    @Builder.Default
    private String state = null;

    @Column(name = "include_national_holidays")
    @Builder.Default
    private Boolean includeNationalHolidays = true;

    @Column(name = "include_state_holidays")
    @Builder.Default
    private Boolean includeStateHolidays = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}