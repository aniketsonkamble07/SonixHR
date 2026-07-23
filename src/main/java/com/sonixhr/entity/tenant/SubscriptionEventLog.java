package com.sonixhr.entity.tenant;

import com.sonixhr.enums.TriggerSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_event_logs", indexes = {
        @Index(name = "idx_event_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_event_subscription_id", columnList = "subscription_id"),
        @Index(name = "idx_event_type", columnList = "event_type"),
        @Index(name = "idx_event_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private TenantSubscription subscription;

    @Column(name = "event_type", nullable = false, length = 50)
    @Builder.Default
    private String eventType = "SUBSCRIPTION_CREATED";  // ← ADD DEFAULT VALUE

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    @Builder.Default
    private TriggerSource triggerSource = TriggerSource.SYSTEM;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}