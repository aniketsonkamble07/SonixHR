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
public class AttendanceDashboardStats {
    private long totalRecords;
    private long presentCount;
    private long absentCount;
    private long lateCount;
    private long halfDayCount;
    private long leaveCount;
    private double attendancePercentage;
    private LocalDate startDate;
    private LocalDate endDate;
}