package com.sonixhr.service.attendance;

import com.sonixhr.repository.attendance.AttendanceRecordRepository;

import com.sonixhr.dto.attendance.*;

import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualAttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final EmployeeRepository employeeRepository;
    private final PermissionService permissionService;
}
