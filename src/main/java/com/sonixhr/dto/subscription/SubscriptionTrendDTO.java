// dto/subscription/SubscriptionTrendDTO.java
package com.sonixhr.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionTrendDTO {
    private List<String> months;
    private Map<String, List<Long>> eventsByMonth;
    private Map<String, List<BigDecimal>> revenueByMonth;
    private Map<String, List<Long>> activeSubscriptionsByMonth;
    private Map<String, Double> growthRate;
    private Integer totalMonthsAnalyzed;
    private String period;
}