package com.sonixhr.entity.tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
 
@Entity
@Table(name = "tenant_usage_stats", indexes = {
        @Index(name = "idx_usage_tenant_date", columnList = "tenant_id, stat_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsageStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "current_employees")
    @Builder.Default
    private Integer currentEmployees = 0;

    @Column(name = "current_storage_mb")
    @Builder.Default
    private Integer currentStorageMb = 0;

    @Column(name = "api_calls_count")
    @Builder.Default
    private Integer apiCallsCount = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
