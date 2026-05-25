package com.sonixhr.dto.employee;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentStat {
    private String department;
    private long count;
}