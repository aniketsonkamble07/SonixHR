package com.sonixhr.dto.attendance;

import com.sonixhr.enums.attendance.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualBulkAttendanceMarkRequest {

    @NotNull(message = "Attendance date is required")
    private LocalDate attendanceDate;

    @NotNull(message = "Attendance map is required")
    private Map<Long, AttendanceStatus> attendanceMap;

    private Map<Long, String> reasonMap;

    private Map<Long, Double> overtimeMap;
}