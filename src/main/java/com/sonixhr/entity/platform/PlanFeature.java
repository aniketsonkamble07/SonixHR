package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "plan_features",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"plan_id", "feature_code"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull(message = "Subscription plan cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @NotBlank(message = "Feature code cannot be blank")
    @Size(min = 2, max = 50, message = "Feature code must be between 2 and 50 characters")
    @Column(name = "feature_code", nullable = false, length = 50)
    private String featureCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;
}
