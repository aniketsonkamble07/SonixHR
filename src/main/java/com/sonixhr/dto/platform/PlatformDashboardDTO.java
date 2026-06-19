package com.sonixhr.dto.platform;

import com.sonixhr.enums.PlanType;
import com.sonixhr.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformDashboardDTO {

    private TenantSummary tenantSummary;
    private SubscriptionSummary subscriptionSummary;
    private SystemSummary systemSummary;
    private List<RecentTenant> recentTenants;
    private List<RegistrationTrendPoint> registrationTrend;
    private List<UpsellOpportunity> upsellOpportunities;
    private List<ResourceConsumer> topResourceConsumers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantSummary {
        private long totalTenants;
        private long activeTenants;
        private long suspendedTenants;
        private long deletedTenants;
        private long trialTenants;
        private Map<String, Long> planDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionSummary {
        private long activePaidSubscriptions;
        private long activeTrials;
        private BigDecimal totalMrr;
        private Map<String, Long> planStatusDistribution;
        private long expiredSubscriptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemSummary {
        private long totalEmployees;
        private long totalPlatformUsers;
        private double averageEmployeesPerTenant;
        private long totalActiveUsers;
        private long supportTicketsOpen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTenant {
        private Long id;
        private String tenantCode;
        private String companyName;
        private String adminName;
        private String adminEmail;
        private PlanType planType;
        private UserStatus status;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistrationTrendPoint {
        private String period;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsellOpportunity {
        private Long id;
        private String tenantCode;
        private String companyName;
        private int currentEmployees;
        private int maxEmployees;
        private double utilizationPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceConsumer {
        private Long id;
        private String tenantCode;
        private String companyName;
        private int currentEmployees;
        private int currentStorageMb;
        private int apiCallsCount;
    }
}
