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
public class PlatformProfessionalTaxSlabDTO {
    private UUID id;
    private IndianState stateCode;
    private String stateName;
    private BigDecimal salaryRangeMin;
    private BigDecimal salaryRangeMax;
    private BigDecimal taxAmount;
    private Integer applicableMonth;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
}
