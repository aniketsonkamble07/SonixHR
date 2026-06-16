package com.sonixhr.controller.leave;

import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.dto.leave.LeaveSettingsDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.enums.leave.WeekendConfig;
import com.sonixhr.service.employee.EmployeeService;
import com.sonixhr.service.leave.LeaveConfigurationService;
import com.sonixhr.service.leave.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveConfigurationService leaveConfigService;
    private final EmployeeService employeeService;

    // =====================================================
    // TENANT LEAVE SETTINGS
    // =====================================================

    @GetMapping("/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_VIEW')")
    public ResponseEntity<TenantLeaveSettings> getTenantLeaveSettings(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get leave settings for tenant: {}", tenantId);
        TenantLeaveSettings settings = leaveConfigService.getTenantSettings(tenantId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<TenantLeaveSettings> updateTenantLeaveSettings(
            @Valid @RequestBody LeaveSettingsDTO dto,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update leave settings for tenant: {}", tenantId);
        TenantLeaveSettings settings = leaveConfigService.updateTenantSettings(tenantId, dto);
        return ResponseEntity.ok(settings);
    }

    // =====================================================
    // EMPLOYEE LEAVE SETTINGS OVERRIDE
    // =====================================================

    @PutMapping("/employees/{employeeId}/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeResponse> updateEmployeeLeaveSettings(
            @PathVariable Long employeeId,
            @RequestParam WeekendConfig weekendConfig,
            @RequestParam(required = false) String customWeekendDays,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update leave settings override for employee: {} by tenant admin: {}", employeeId, currentEmployee.getId());
        leaveConfigService.updateEmployeeSettings(tenantId, employeeId, weekendConfig, customWeekendDays);
        EmployeeResponse response = employeeService.getEmployeeById(employeeId, tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // LEAVE REQUESTS
    // =====================================================

    @PostMapping
    public ResponseEntity<LeaveResponseDTO> requestLeave(
            @Valid @RequestBody LeaveRequestDTO request,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to request leave for employee: {}", currentEmployee.getId());
        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(currentEmployee.getId(), request, currentEmployee);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/my")
    public ResponseEntity<java.util.List<LeaveResponseDTO>> getMyLeaves(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get own leaves for employee: {}", currentEmployee.getId());
        java.util.List<LeaveResponseDTO> response = leaveService.getMyLeaves(currentEmployee.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/team")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'LEAVE_VIEW_TEAM')")
    public ResponseEntity<Page<LeaveResponseDTO>> getTeamLeaveRequests(
            @RequestParam(required = false) LeaveStatus status,
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to get team leave requests for manager: {}", currentEmployee.getId());
        Page<LeaveResponseDTO> response = leaveService.getTeamLeaveRequests(
                currentEmployee.getId(), currentEmployee.getTenantId(), status, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_ANY') or @permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_DEPARTMENT') or #currentEmployee.isManager()")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to approve leave request: {} by {}", id, currentEmployee.getFullName());
        LeaveResponseDTO response = leaveService.approveLeave(id, currentEmployee.getId(), currentEmployee.getFullName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_ANY') or @permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_DEPARTMENT') or #currentEmployee.isManager()")
    public ResponseEntity<LeaveResponseDTO> rejectLeave(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to reject leave request: {} by {}", id, currentEmployee.getFullName());
        LeaveResponseDTO response = leaveService.rejectLeave(id, rejectionReason, currentEmployee.getId(), currentEmployee.getFullName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<LeaveResponseDTO> cancelLeave(
            @PathVariable Long id,
            @RequestParam(required = false) String cancellationReason,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to cancel leave request: {} by employee: {}", id, currentEmployee.getId());
        LeaveResponseDTO response = leaveService.cancelLeave(id, currentEmployee.getId(), cancellationReason);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getLeaveBalance(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get leave balance for employee: {}", currentEmployee.getId());
        Map<String, Object> balance = leaveService.getLeaveBalanceWithTenantSettings(currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok(balance);
    }
}