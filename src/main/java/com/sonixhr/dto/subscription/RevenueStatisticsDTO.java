// dto/subscription/RevenueStatisticsDTO.java
package com.sonixhr.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatisticsDTO {
    private BigDecimal totalRevenue;
    private BigDecimal averageRevenuePerMonth;
    private BigDecimal highestRevenueMonth;
    private String highestRevenueMonthName;
    private BigDecimal lowestRevenueMonth;
    private String lowestRevenueMonthName;
    private Map<String, BigDecimal> revenueByMonth;
    private Map<String, Long> subscriptionsByMonth;
    private BigDecimal revenueGrowthRate;
    private Integer totalMonthsAnalyzed;
    private String currency;
    private Integer year;
}