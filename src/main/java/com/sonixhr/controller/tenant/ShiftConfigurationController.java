package com.sonixhr.controller.tenant;

import com.sonixhr.dto.attendance.ShiftConfigurationDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationSummaryDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.service.attendance.ShiftConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/shift-configurations")
@RequiredArgsConstructor
public class ShiftConfigurationController {

    private final ShiftConfigurationService shiftConfigurationService;

    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_CREATE')")
    public ResponseEntity<ShiftConfigurationDTO> createShift(
            @Valid @RequestBody ShiftConfigurationRequestDTO request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Creating shift for tenant: {} by employee: {}", tenantId, employeeId);

        ShiftConfigurationDTO created = shiftConfigurationService.createShiftConfiguration(
                request, tenantId, employeeId);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_UPDATE')")
    public ResponseEntity<ShiftConfigurationDTO> updateShift(
            @PathVariable Long id,
            @Valid @RequestBody ShiftConfigurationRequestDTO request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Updating shift: {} for tenant: {} by employee: {}", id, tenantId, employeeId);

        ShiftConfigurationDTO updated = shiftConfigurationService.updateShiftConfiguration(
                id, request, tenantId, employeeId);

        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'SHIFT_ADMIN')")
    public ResponseEntity<Void> activateShift(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Activating shift: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        shiftConfigurationService.activateShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'SHIFT_ADMIN')")
    public ResponseEntity<Void> deactivateShift(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Deactivating shift: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        shiftConfigurationService.deactivateShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'SHIFT_ADMIN')")
    public ResponseEntity<Void> setDefaultShift(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Setting shift as default: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        shiftConfigurationService.setDefaultShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @permissionEvaluator.hasPermission(authentication, 'SHIFT_DELETE')")
    public ResponseEntity<Void> softDeleteShift(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        Long employeeId = currentEmployee.getId();

        log.info("Soft deleting shift: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
        shiftConfigurationService.softDeleteShiftConfiguration(id, tenantId, employeeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> hardDeleteShift(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();

        log.warn("Hard deleting shift: {} for tenant: {} by employee: {}", id, tenantId, currentEmployee.getId());
        shiftConfigurationService.hardDeleteShiftConfiguration(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // GET endpoints
    @GetMapping("/my-shift")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW')")
    public ResponseEntity<ShiftConfigurationDTO> getMyShift(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftConfiguration(tenantId);
        return ResponseEntity.ok(shift);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW')")
    public ResponseEntity<ShiftConfigurationDTO> getShiftById(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftConfigurationById(id, tenantId);
        return ResponseEntity.ok(shift);
    }

    @GetMapping("/code/{shiftCode}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW')")
    public ResponseEntity<ShiftConfigurationDTO> getShiftByCode(
            @PathVariable String shiftCode,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftByCode(shiftCode, tenantId);
        return ResponseEntity.ok(shift);
    }

    @GetMapping("/all")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW_ALL')")
    public ResponseEntity<List<ShiftConfigurationSummaryDTO>> getAllShifts(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = currentEmployee.getTenantId();
        List<ShiftConfigurationSummaryDTO> shifts = shiftConfigurationService.getAllShiftConfigurationsSummary(tenantId);
        return ResponseEntity.ok(shifts);
    }

    @GetMapping("/effective")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW')")
    public ResponseEntity<ShiftConfigurationDTO> getEffectiveShift(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        ShiftConfigurationDTO shift = shiftConfigurationService.getEffectiveShiftOnDate(tenantId, date);
        return ResponseEntity.ok(shift);
    }

    @GetMapping("/active")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SHIFT_VIEW_ALL')")
    public ResponseEntity<List<ShiftConfigurationSummaryDTO>> getAllActiveShifts(
            @AuthenticationPrincipal Employee currentEmployee) {
        List<ShiftConfigurationSummaryDTO> shifts = shiftConfigurationService.getAllActiveShiftsSummary(currentEmployee.getTenantId());
        return ResponseEntity.ok(shifts);
    }

    // Utility endpoints
    @GetMapping("/validate-checkin")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_MARK')")
    public ResponseEntity<Boolean> isValidCheckinTime(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam LocalTime checkinTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        boolean isValid = shiftConfigurationService.isValidCheckinTime(tenantId, checkinTime, date);
        return ResponseEntity.ok(isValid);
    }

    @GetMapping("/late-minutes")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Integer> calculateLateMinutes(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam LocalTime checkinTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        int lateMinutes = shiftConfigurationService.calculateLateMinutes(tenantId, checkinTime, date);
        return ResponseEntity.ok(lateMinutes);
    }

    @GetMapping("/early-exit-minutes")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Integer> calculateEarlyExitMinutes(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam LocalTime checkoutTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        int earlyExitMinutes = shiftConfigurationService.calculateEarlyExitMinutes(tenantId, checkoutTime, date);
        return ResponseEntity.ok(earlyExitMinutes);
    }

    @GetMapping("/is-weekoff")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Boolean> isWeekOff(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        boolean isWeekOff = shiftConfigurationService.isWeekOff(tenantId, date);
        return ResponseEntity.ok(isWeekOff);
    }

    @GetMapping("/expected-hours")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Double> getExpectedWorkingHours(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        double hours = shiftConfigurationService.getExpectedWorkingHours(tenantId, date);
        return ResponseEntity.ok(hours);
    }

    @GetMapping("/working-hours")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Double> calculateWorkingHours(
            @RequestParam LocalTime checkIn,
            @RequestParam LocalTime checkOut,
            @RequestParam(required = false) Integer breakMinutes) {

        double hours = shiftConfigurationService.calculateWorkingHours(checkIn, checkOut, breakMinutes);
        return ResponseEntity.ok(hours);
    }

    @GetMapping("/overtime")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<Double> calculateOvertime(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam LocalTime checkoutTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        double overtime = shiftConfigurationService.calculateOvertime(tenantId, checkoutTime, date);
        return ResponseEntity.ok(overtime);
    }

    @GetMapping("/attendance-status")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<String> getAttendanceStatus(
            @AuthenticationPrincipal Employee currentEmployee,
            @RequestParam(required = false) LocalTime checkinTime,
            @RequestParam(required = false) Double workingHours,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long tenantId = currentEmployee.getTenantId();
        String status = shiftConfigurationService.determineStatus(tenantId, checkinTime, date, workingHours);
        return ResponseEntity.ok(status);
    }
}