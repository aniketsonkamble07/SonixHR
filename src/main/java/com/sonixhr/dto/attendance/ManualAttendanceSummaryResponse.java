package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAttendanceSummaryResponse {

    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LocalDate hireDate;
    private int year;
    private int month;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int totalDaysInMonth;
    private long present;
    private long absent;
    private long halfDay;
    private long late;
    private long onLeave;
    private double totalOvertimeHours;
    private double attendanceRate;
    private String message;
}