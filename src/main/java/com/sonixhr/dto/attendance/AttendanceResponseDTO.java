// dto/attendance/AttendanceResponseDTO.java
package com.sonixhr.dto.attendance;

import com.sonixhr.enums.attendance.AttendanceMethod;
import com.sonixhr.enums.attendance.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponseDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String departmentName;
    private LocalDate date;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private Integer breakMinutes;
    private Double workingHours;
    private Double overtimeHours;
    private AttendanceStatus status;
    private String statusDisplay;
    private AttendanceMethod method;
    private String remarks;
    private String deviceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}