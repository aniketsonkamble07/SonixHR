package com.sonixhr.dto.attendance;

import com.sonixhr.enums.attendance.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAttendanceRecordResponse {

    private Long id;
    private Long tenantId;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LocalDate attendanceDate;
    private AttendanceStatus status;
    private Double overtimeHours;
    private String reason;
    private String markedByName;
    private String markedByRole;
    private LocalDateTime markedAt;
    private LocalDateTime updatedAt;
}