package com.sonixhr.dto.employee;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCompensationPeriodResponse {
    private Long employeeId;
    private List<ProfilePeriodInfo> activeProfiles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfilePeriodInfo {
        private UUID id;
        private BigDecimal monthlyCtc;
        private String currency;
        private String taxRegime;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private List<ComponentOverrideInfo> componentOverrides;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentOverrideInfo {
        private UUID id;
        private String componentCode;
        private BigDecimal amount;
        private String customExpression;

        // Frontend compatibility fields
        private BigDecimal overrideValue;
        private String overrideType;
    }
}
