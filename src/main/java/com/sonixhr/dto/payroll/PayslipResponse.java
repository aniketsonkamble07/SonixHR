package com.sonixhr.dto.payroll;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipResponse {
    private UUID id;
    private UUID payrunId;
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private String departmentName;
    private String designation;
    private Integer month;
    private Integer year;
    
    private BigDecimal grossEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    private BigDecimal lopDays;
    private BigDecimal wagesBase;
    
    private List<PayslipItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayslipItemDto {
        private UUID id;
        private String componentCode;
        private String componentName;
        private String type; // ALLOWANCE, DEDUCTION
        private BigDecimal amount;
        private String expressionUsed;
    }
}
