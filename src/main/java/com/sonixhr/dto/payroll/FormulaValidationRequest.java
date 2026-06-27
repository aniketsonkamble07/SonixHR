package com.sonixhr.dto.payroll;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormulaValidationRequest {
    private String formula;
    private String componentCode;
}