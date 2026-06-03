// dto/attendance/AttendanceSummaryDTO.java
package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDTO {
    private Long totalEmployees;
    private Long presentToday;
    private Long lateToday;
    private Long absentToday;
    private Long onLeaveToday;
    private Double averageWorkingHours;
    private Map<String, Long> departmentWisePresent;
}