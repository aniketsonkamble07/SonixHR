package com.sonixhr.dto.employee;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCompensationResponse {
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private Map<String, Object> bankDetails;
    private ActiveSalaryProfileInfo activeSalaryProfile;
    private List<ComponentOverrideInfo> componentOverrides;
    private List<SalaryProfileHistoryInfo> salaryHistory;

    // Root-level compatibility fields expected by frontend
    private BigDecimal monthlyCtc;
    private String currency;
    private String taxRegime;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSalaryProfileInfo {
        private UUID id;
        private BigDecimal monthlyCtc;
        private String currency;
        private String taxRegime;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryProfileHistoryInfo {
        private UUID id;
        private BigDecimal monthlyCtc;
        private String currency;
        private String taxRegime;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
    }
}
