package com.sonixhr.dto.attendance;

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
public class ManualAttendanceCalendarResponse {

    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private int year;
    private int month;
    private Map<LocalDate, CalendarDayInfo> calendar;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarDayInfo {
        private LocalDate date;
        private String dayOfWeek;
        private String status;
        private String reason;
        private Double overtimeHours;
        private String markedBy;
        private boolean isWeekend;
        private boolean isHoliday;
    }
}