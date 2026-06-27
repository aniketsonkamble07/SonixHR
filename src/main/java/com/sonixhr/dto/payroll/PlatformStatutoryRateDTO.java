package com.sonixhr.dto.payroll;

import com.sonixhr.enums.IndianState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatutoryRateDTO {
    private UUID id;
    private String componentCode;
    private String componentName;
    private BigDecimal rate;
    private BigDecimal ceilingAmount;
    private BigDecimal capAmount;
    private String applicableTo;
    private IndianState stateCode;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
    private String description;
}
