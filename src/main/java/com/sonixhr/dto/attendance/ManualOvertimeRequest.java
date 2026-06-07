package com.sonixhr.dto.attendance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualOvertimeRequest {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotNull(message = "Overtime hours are required")
    @DecimalMin(value = "0.0", message = "Overtime hours cannot be negative")
    @DecimalMax(value = "12.0", message = "Overtime cannot exceed 12 hours per day")
    private Double overtimeHours;

    private String reason;
}