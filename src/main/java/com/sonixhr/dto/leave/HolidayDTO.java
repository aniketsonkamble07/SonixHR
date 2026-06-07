package com.sonixhr.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolidayDTO {

    private Long id;
    private LocalDate date;
    private String name;
    private String type;
    private String region;
    private boolean isRecurring;
    private Integer year;
    private String description;
}