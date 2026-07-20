package com.sonixhr.entity.tenant;

import com.sonixhr.enums.TriggerSource;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscription_events",
       indexes = {
           @Index(name = "idx_subscription_events_tenant", columnList = "tenant_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull(message = "Tenant cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_subscription_id")
    private TenantSubscription tenantSubscription;

    @Column(name = "from_status", length = 50)
    private String fromStatus;

    @NotNull(message = "Target status cannot be null")
    @Column(name = "to_status", nullable = false, length = 50)
    private String toStatus;

    @NotNull(message = "Trigger source cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 50)
    private TriggerSource triggeredBy;

    @Column(name = "triggered_by_id")
    private Long triggeredById;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
