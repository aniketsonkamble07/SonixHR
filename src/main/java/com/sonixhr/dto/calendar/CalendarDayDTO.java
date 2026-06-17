package com.sonixhr.dto.calendar;

import com.sonixhr.enums.calendar.CalendarAttendanceStatus;
import com.sonixhr.enums.calendar.CalendarDayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.time.LocalDate;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayDTO {

    private LocalDate date;
    private int dayOfMonth;
    private String dayOfWeek;
    private int dayOfWeekValue;

    private CalendarDayType type;
    private CalendarAttendanceStatus status;
    private String displayName;
    private String color;
    private String description;
    private Double overtimeHours;
    private String leaveType;

    private boolean isWeekend;
    private boolean isPast;
    private boolean isToday;
}