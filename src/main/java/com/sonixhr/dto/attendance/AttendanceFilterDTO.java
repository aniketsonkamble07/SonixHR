package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceFilterDTO {
    private UUID tenantId;
    private Long employeeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
