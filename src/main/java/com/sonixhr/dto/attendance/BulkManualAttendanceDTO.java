package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkManualAttendanceDTO {
    private UUID tenantId;
    private List<ManualAttendanceRequestDTO> attendances;
    private Long createdBy;
}