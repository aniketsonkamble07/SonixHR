// entity/tenant/SubscriptionHistory.java
package com.sonixhr.entity.tenant;

import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.entity.platform.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_history", indexes = {
                @Index(name = "idx_sub_history_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sub_history_event_date", columnList = "event_date"),
                @Index(name = "idx_sub_history_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_sub_history_event_type", columnList = "event_type"),
                @Index(name = "idx_sub_history_status", columnList = "plan_status"),
                @Index(name = "idx_sub_history_tenant_date", columnList = "tenant_id, event_date DESC"),
                @Index(name = "idx_sub_history_tenant_status", columnList = "tenant_id, plan_status"),
                @Index(name = "idx_sub_history_plan_id", columnList = "plan_id")
})
public class SubscriptionHistory {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "tenant_id", nullable = false)
        private Long tenantId;

        @Column(name = "subscription_id", length = 50)
        private String subscriptionId;

        @Column(name = "plan_id")
        private Long planId;

        @Column(name = "plan_code", length = 50)
        private String planCode;

        @Column(name = "plan_name", length = 100)
        private String planName;

        @Column(name = "employee_id")
        private Long employeeId;

        @Column(name = "employee_name", length = 100)
        private String employeeName;

        @Column(name = "employee_email", length = 100)
        private String employeeEmail;

        @Column(name = "plan_type", length = 30)
        private String planType;

        @Enumerated(EnumType.STRING)
        @Column(name = "plan_status", length = 20)
        private PlanStatus planStatus;

        @Column(name = "event_type", nullable = false, length = 30)
        private String eventType;

        @Column(name = "event_date", nullable = false)
        private LocalDateTime eventDate;

        @Column(name = "start_date")
        private LocalDateTime startDate;

        @Column(name = "end_date")
        private LocalDateTime endDate;

        @Column(name = "amount", precision = 19, scale = 2)
        private BigDecimal amount;

        @Column(name = "currency", length = 3)
        private String currency;

        @Column(name = "payment_method", length = 50)
        private String paymentMethod;

        @Column(name = "transaction_id", length = 100)
        private String transactionId;

        @Column(name = "invoice_number", length = 50)
        private String invoiceNumber;

        @Column(name = "reason", length = 500)
        private String reason;

        @Column(name = "notes", length = 1000)
        private String notes;

        // Plan change tracking fields
        @Column(name = "previous_plan_id")
        private Long previousPlanId;

        @Column(name = "previous_plan_code", length = 50)
        private String previousPlanCode;

        @Column(name = "previous_plan_name", length = 100)
        private String previousPlanName;

        @Column(name = "previous_price", precision = 19, scale = 2)
        private BigDecimal previousPrice;

        @Column(name = "new_price", precision = 19, scale = 2)
        private BigDecimal newPrice;

        @Column(name = "previous_max_employees")
        private Integer previousMaxEmployees;

        @Column(name = "new_max_employees")
        private Integer newMaxEmployees;

        @Column(name = "previous_validity_months")
        private Integer previousValidityMonths;

        @Column(name = "new_validity_months")
        private Integer newValidityMonths;

        @Column(name = "field_changed", length = 255)
        private String fieldChanged;

        @Column(name = "old_value", length = 1000)
        private String oldValue;

        @Column(name = "new_value", length = 1000)
        private String newValue;

        @Column(name = "is_auto_renew")
        private Boolean isAutoRenew;

        @Column(name = "grace_period_days")
        private Integer gracePeriodDays;

        @Column(name = "days_remaining")
        private Integer daysRemaining;

        @Column(name = "cancellation_type", length = 20)
        private String cancellationType;

        @Enumerated(EnumType.STRING)
        @Column(name = "trigger_source")
        private TriggerSource triggerSource;

        @Column(name = "triggered_by_id")
        private Long triggeredById;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "metadata", columnDefinition = "jsonb")
        private String metadata;

        @CreationTimestamp
        @Column(name = "created_at", updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "created_by", length = 100)
        private String createdBy;

        @UpdateTimestamp
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        @Column(name = "updated_by", length = 100)
        private String updatedBy;

        @Column(name = "previous_status")
        private String previousStatus;

        @Column(name = "new_status")
        private String newStatus;

        @Column(name = "previous_plan_type")
        private String previousPlanType;

        @Column(name = "new_plan_type")
        private String newPlanType;
}