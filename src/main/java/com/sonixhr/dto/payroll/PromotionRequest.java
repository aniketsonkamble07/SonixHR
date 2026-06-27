package com.sonixhr.dto.payroll;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PromotionRequest {
    private BigDecimal newCtc;
    private String promotionReason;
    private LocalDate effectiveFrom;
    private List<EmployeeComponentOverrideDTO> componentOverrides;
    private Long modifiedBy;
}