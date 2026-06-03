
package com.sonixhr.dto.attendance;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAttendanceRequestDTO {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    private Integer breakMinutes;

    private String remarks;
}