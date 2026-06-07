package com.sonixhr.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveCalendarDTO {

    private int year;
    private int month;
    private Long employeeId;
    private String employeeName;
    private List<CalendarDay> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarDay {
        private LocalDate date;
        private int dayOfMonth;
        private String dayOfWeek;
        private boolean isWeekend;
        private boolean isHoliday;
        private String holidayName;
        private boolean isOnLeave;
        private String leaveType;
        private String leaveReason;
        private String attendanceStatus;
        private Double overtimeHours;
    }
}