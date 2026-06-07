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
public class ManualDashboardStatsResponse {

    private LocalDate date;
    private long totalEmployees;
    private long present;
    private long absent;
    private long onLeave;
    private long pending;
    private double attendancePercentage;
}