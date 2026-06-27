package com.sonixhr.dto.payroll;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormulaValidationResponse {
    private boolean valid;
    private String formula;
    private String message;
}