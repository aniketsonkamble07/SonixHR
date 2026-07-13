package com.sonixhr.service.payroll;

import com.sonixhr.entity.payroll.EmployeeSalaryProfile;
import java.time.LocalDate;

public record ProfileSegment(
        EmployeeSalaryProfile profile,
        LocalDate start,
        LocalDate end,
        int activeDays) {}
