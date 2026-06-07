package com.sonixhr.entity.leave;

import com.sonixhr.enums.leave.WeekendConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_leave_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantLeaveSettings {

    @Id
    private Long tenantId;

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

    @Column(name = "casual_leave_per_year")
    @Builder.Default
    private Integer casualLeavePerYear = 12;

    @Column(name = "sick_leave_per_year")
    @Builder.Default
    private Integer sickLeavePerYear = 12;

    @Column(name = "earned_leave_per_year")
    @Builder.Default
    private Integer earnedLeavePerYear = 15;

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