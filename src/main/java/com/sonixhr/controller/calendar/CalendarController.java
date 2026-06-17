package com.sonixhr.controller.calendar;

import com.sonixhr.dto.calendar.CalendarMonthDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.calendar.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CalendarController {

    private final CalendarService calendarService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/my")
    public ResponseEntity<CalendarMonthDTO> getMyCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal Employee currentEmployee) {
        
        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();

        log.info("REST request to get consolidated calendar for logged-in employee: {} for {}-{}", 
                currentEmployee.getId(), targetYear, targetMonth);

        CalendarMonthDTO calendar = calendarService.getEmployeeCalendar(
                currentEmployee.getId(), 
                currentEmployee.getTenantId(), 
                targetYear, 
                targetMonth
        );
        return ResponseEntity.ok(calendar);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_VIEW_TEAM', 'LEAVE_VIEW_TEAM', 'SUPER_ADMIN')")
    public ResponseEntity<CalendarMonthDTO> getEmployeeCalendar(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal Employee currentEmployee) {
        
        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();

        log.info("REST request to get consolidated calendar for employee: {} by user: {} for {}-{}", 
                employeeId, currentEmployee.getId(), targetYear, targetMonth);

        // Security check: Only allow if super admin, or if target employee reports to the manager, or is the employee themselves
        if (!currentEmployee.isSuperAdmin() && !currentEmployee.getId().equals(employeeId)) {
            Employee targetEmployee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            
            if (targetEmployee.getManager() == null || 
                    !targetEmployee.getManager().getId().equals(currentEmployee.getId())) {
                throw new BusinessException("You can only view calendar for employees who report to you");
            }
        }

        CalendarMonthDTO calendar = calendarService.getEmployeeCalendar(
                employeeId, 
                currentEmployee.getTenantId(), 
                targetYear, 
                targetMonth
        );
        return ResponseEntity.ok(calendar);
    }
}
