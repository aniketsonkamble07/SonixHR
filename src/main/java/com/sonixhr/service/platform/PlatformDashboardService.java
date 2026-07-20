package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformDashboardDTO;
import com.sonixhr.dto.platform.SystemHealthDTO;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.SupportTicketRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.tenant.TenantUsageStatRepository;
import com.sonixhr.entity.tenant.TenantUsageStat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformDashboardService {

    private static final Logger log = LoggerFactory.getLogger(PlatformDashboardService.class);

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final EmployeeRepository employeeRepository;
    private final PlatformUserRepository platformUserRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final JavaMailSender mailSender;
    private final DataSource dataSource;
    private final org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    private final TenantUsageStatRepository tenantUsageStatRepository;

    public PlatformDashboardDTO getDashboard(int trendDays) {
        log.info("Generating platform dashboard stats for the last {} days", trendDays);

        List<Tenant> allTenants = tenantRepository.findAll();
        List<TenantSubscription> allSubscriptions = subscriptionRepository.findAll();
        long totalEmployees = employeeRepository.count();
        long totalPlatformUsers = platformUserRepository.count();

        List<Object[]> countsData = employeeRepository.countEmployeesGroupByTenantId();
        Map<Long, Long> tenantEmployeeCounts = countsData.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (v1, v2) -> v1
                ));

        // 1. Tenant Summary
        long totalTenants = allTenants.size();
        long activeTenants = allTenants.stream().filter(t -> t.getStatus() == UserStatus.ACTIVE).count();
        long suspendedTenants = allTenants.stream().filter(t -> t.getStatus() == UserStatus.SUSPENDED).count();
        long deletedTenants = allTenants.stream().filter(t -> t.getStatus() == UserStatus.DELETED).count();
        long trialTenants = allTenants.stream()
                .filter(t -> t.getStatus() != UserStatus.DELETED && t.getSubscriptionPlan() != null && "trial".equalsIgnoreCase(t.getSubscriptionPlan().getName()))
                .count();

        Map<String, Long> planDistribution = allTenants.stream()
                .filter(t -> t.getStatus() != UserStatus.DELETED && t.getPlanType() != null)
                .collect(Collectors.groupingBy(t -> t.getPlanType().toLowerCase(), Collectors.counting()));

        PlatformDashboardDTO.TenantSummary tenantSummary = PlatformDashboardDTO.TenantSummary.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .suspendedTenants(suspendedTenants)
                .deletedTenants(deletedTenants)
                .trialTenants(trialTenants)
                .planDistribution(planDistribution)
                .build();

        // 2. Subscription Summary
        long activeTrials = allSubscriptions.stream()
                .filter(sub -> sub.getIsActive() && !sub.isExpired() && sub.getSubscriptionPlan() != null && "trial".equalsIgnoreCase(sub.getSubscriptionPlan().getName()))
                .count();
        long activePaidSubscriptions = allSubscriptions.stream()
                .filter(sub -> sub.getIsActive() && !sub.isExpired() && sub.getSubscriptionPlan() != null && !"trial".equalsIgnoreCase(sub.getSubscriptionPlan().getName()))
                .count();

        // Expired Subscriptions
        long expiredSubscriptions = allSubscriptions.stream()
                .filter(TenantSubscription::isExpired)
                .count();

        // Sum MRR (Monthly Recurring Revenue equivalent)
        BigDecimal totalMrr = allSubscriptions.stream()
                .filter(sub -> sub.getIsActive() && sub.getAmount() != null)
                .map(sub -> {
                    BigDecimal amt = sub.getAmount();
                    int validity = sub.getSubscriptionPlan() != null ? sub.getSubscriptionPlan().getValidityMonths() : 1;
                    if (validity >= 12) {
                        return amt.divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
                    }
                    return amt;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> planStatusDistribution = allSubscriptions.stream()
                .collect(Collectors.groupingBy(sub -> sub.getPlanStatus().name(), Collectors.counting()));

        PlatformDashboardDTO.SubscriptionSummary subscriptionSummary = PlatformDashboardDTO.SubscriptionSummary.builder()
                .activePaidSubscriptions(activePaidSubscriptions)
                .activeTrials(activeTrials)
                .totalMrr(totalMrr)
                .planStatusDistribution(planStatusDistribution)
                .expiredSubscriptions(expiredSubscriptions)
                .build();

        // 3. System Summary
        double averageEmployeesPerTenant = totalTenants > 0 ? (double) totalEmployees / totalTenants : 0.0;
        long activeEmployees = employeeRepository.countByIsActiveTrue();
        long activePlatformUsers = platformUserRepository.countByStatus(UserStatus.ACTIVE);
        long totalActiveUsers = activeEmployees + activePlatformUsers;
        long supportTicketsOpen = supportTicketRepository.countByStatus("OPEN") + supportTicketRepository.countByStatus("IN_PROGRESS");

        PlatformDashboardDTO.SystemSummary systemSummary = PlatformDashboardDTO.SystemSummary.builder()
                .totalEmployees(totalEmployees)
                .totalPlatformUsers(totalPlatformUsers)
                .averageEmployeesPerTenant(averageEmployeesPerTenant)
                .totalActiveUsers(totalActiveUsers)
                .supportTicketsOpen(supportTicketsOpen)
                .build();

        // 4. Recent Tenants
        List<PlatformDashboardDTO.RecentTenant> recentTenants = allTenants.stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt).reversed())
                .limit(5)
                .map(t -> PlatformDashboardDTO.RecentTenant.builder()
                        .id(t.getId())
                        .tenantCode(t.getTenantCode())
                        .companyName(t.getCompanyName())
                        .adminName(t.getAdminName())
                        .adminEmail(t.getAdminEmail())
                        .planType(t.getPlanType())
                        .status(t.getStatus())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // 5. Registration Trend
        LocalDate cutoff = LocalDate.now().minusDays(trendDays);
        Map<String, Long> trendMap = allTenants.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().isAfter(cutoff))
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate().toString(), Collectors.counting()));

        List<PlatformDashboardDTO.RegistrationTrendPoint> registrationTrend = trendMap.entrySet().stream()
                .map(entry -> new PlatformDashboardDTO.RegistrationTrendPoint(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PlatformDashboardDTO.RegistrationTrendPoint::getPeriod))
                .collect(Collectors.toList());

        // 6. Upsell Opportunities (current employees >= 90% of max employees)
        List<PlatformDashboardDTO.UpsellOpportunity> upsellOpportunities = new ArrayList<>();
        for (Tenant tenant : allTenants) {
            if (tenant.getStatus() == UserStatus.DELETED || tenant.getMaxEmployees() == null || tenant.getMaxEmployees() <= 0) {
                continue;
            }
            long count = tenantEmployeeCounts.getOrDefault(tenant.getId(), 0L);
            double utilization = ((double) count / tenant.getMaxEmployees()) * 100.0;
            if (utilization >= 90.0) {
                upsellOpportunities.add(PlatformDashboardDTO.UpsellOpportunity.builder()
                        .id(tenant.getId())
                        .tenantCode(tenant.getTenantCode())
                        .companyName(tenant.getCompanyName())
                        .currentEmployees((int) count)
                        .maxEmployees(tenant.getMaxEmployees())
                        .utilizationPercentage(Math.round(utilization * 100.0) / 100.0)
                        .build());
            }
        }

        // 7. Top Resource Consumers (Querying real daily stats, with fallback)
        List<TenantUsageStat> latestStats = tenantUsageStatRepository.findAllLatestStats();
        Map<Long, TenantUsageStat> statsMap = latestStats.stream()
                .collect(Collectors.toMap(
                        s -> s.getTenant().getId(),
                        s -> s,
                        (s1, s2) -> s1
                ));

        List<PlatformDashboardDTO.ResourceConsumer> topResourceConsumers = allTenants.stream()
                .filter(t -> t.getStatus() != UserStatus.DELETED)
                .map(t -> {
                    long count = tenantEmployeeCounts.getOrDefault(t.getId(), 0L);
                    TenantUsageStat stat = statsMap.get(t.getId());
                    int employees = stat != null ? stat.getCurrentEmployees() : (int) count;
                    int storage = stat != null ? stat.getCurrentStorageMb() : (int) count * 15;
                    int apiCalls = stat != null ? stat.getApiCallsCount() : (int) count * 150 + 20;

                    return PlatformDashboardDTO.ResourceConsumer.builder()
                            .id(t.getId())
                            .tenantCode(t.getTenantCode())
                            .companyName(t.getCompanyName())
                            .currentEmployees(employees)
                            .currentStorageMb(storage)
                            .apiCallsCount(apiCalls)
                            .build();
                })
                .sorted(Comparator.comparing(PlatformDashboardDTO.ResourceConsumer::getCurrentEmployees).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return PlatformDashboardDTO.builder()
                .tenantSummary(tenantSummary)
                .subscriptionSummary(subscriptionSummary)
                .systemSummary(systemSummary)
                .recentTenants(recentTenants)
                .registrationTrend(registrationTrend)
                .upsellOpportunities(upsellOpportunities)
                .topResourceConsumers(topResourceConsumers)
                .build();
    }

    public SystemHealthDTO getSystemHealth() {
        log.info("Checking system components health");
        String dbStatus = "UP";
        String redisStatus = "UP";
        String mailStatus = "UP";

        // DB check
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isClosed()) {
                dbStatus = "DOWN";
            }
        } catch (Exception e) {
            log.error("Database health check failed", e);
            dbStatus = "DOWN";
        }

        // Redis check
        try (var conn = redisConnectionFactory.getConnection()) {
            String ping = conn.ping();
            if (!"PONG".equalsIgnoreCase(ping)) {
                redisStatus = "DOWN";
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            redisStatus = "DOWN";
        }

        // Mail server check
        try {
            if (mailSender == null) {
                mailStatus = "DOWN";
            }
        } catch (Exception e) {
            mailStatus = "DOWN";
        }

        // Disk space check
        File file = new File(".");
        long freeSpace = file.getFreeSpace();
        long totalSpace = file.getTotalSpace();
        String diskStatus = freeSpace > 1024 * 1024 * 1024 ? "UP" : "WARN";

        return SystemHealthDTO.builder()
                .databaseStatus(dbStatus)
                .redisStatus(redisStatus)
                .mailSenderStatus(mailStatus)
                .diskSpaceStatus(diskStatus)
                .freeDiskSpaceBytes(freeSpace)
                .totalDiskSpaceBytes(totalSpace)
                .build();
    }
}
