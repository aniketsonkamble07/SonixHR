package com.sonixhr.dto.attendance;

import com.sonixhr.enums.attendance.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualQuickMarkRequest {

    @NotNull(message = "Attendance status is required")
    private AttendanceStatus status;

    private String reason;
}