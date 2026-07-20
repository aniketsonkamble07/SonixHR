package com.sonixhr.dto.employee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeeCompensationRequest {

    private Long employeeId;
    private BigDecimal monthlyCtc;
    private String currency; // Defaults to "INR"
    private String taxRegime; // e.g. "NEW_REGIME" / "OLD_REGIME"
    private LocalDate effectiveFrom; // Defaults to current date if null

    private Map<String, Object> bankDetails;

    private List<ComponentOverrideRequest> componentOverrides;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentOverrideRequest {
        @NotNull(message = "Component code is required")
        private String componentCode;
        private BigDecimal amount;
        private String customExpression;

        // Frontend compatibility fields
        private String overrideType;
        private BigDecimal overrideValue;
    }
}
