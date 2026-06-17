package com.sonixhr.dto.calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthDTO {
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private int year;
    private int month;
    private String monthName;
    private String monthDisplayName;
    private List<CalendarDayDTO> days;
    private Map<String, Object> summary;
}