package com.sonixhr.controller.leave;

import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.dto.leave.LeaveSettingsDTO;
import com.sonixhr.entity.employee.Employee;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class LeaveManagementController {

    private final LeaveService leaveService;
    private final LeaveConfigurationService leaveConfigService;
    private final EmployeeService employeeService;

    // =====================================================
    // TENANT LEAVE SETTINGS
    // =====================================================

    @GetMapping("/leaves/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_VIEW')")
    public ResponseEntity<LeaveSettingsDTO> getTenantLeaveSettings(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get leave settings for tenant: {}", tenantId);
        LeaveSettingsDTO settings = leaveConfigService.getTenantSettingsDTO(tenantId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/leaves/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<LeaveSettingsDTO> updateTenantLeaveSettings(
            @Valid @RequestBody LeaveSettingsDTO dto,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update leave settings for tenant: {}", tenantId);
        LeaveSettingsDTO settings = leaveConfigService.updateTenantSettingsDTO(tenantId, dto);
        return ResponseEntity.ok(settings);
    }

    // =====================================================
    // EMPLOYEE LEAVE SETTINGS OVERRIDE
    // =====================================================

    @PutMapping("/{employeeId}/leave-settings")
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
    // TEAM LEAVE REQUESTS
    // =====================================================

    @GetMapping("/leaves/team")
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

    // =====================================================
    // APPROVE / REJECT LEAVE
    // =====================================================

    @PutMapping("/leaves/{id}/approve")
    @PreAuthorize("@leaveSecurity.canApproveOrReject(#id, #currentEmployee)")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to approve leave request: {} by {}", id, currentEmployee.getFullName());
        LeaveResponseDTO response = leaveService.approveLeave(id, currentEmployee.getId(), currentEmployee.getFullName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/leaves/{id}/reject")
    @PreAuthorize("@leaveSecurity.canApproveOrReject(#id, #currentEmployee)")
    public ResponseEntity<LeaveResponseDTO> rejectLeave(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to reject leave request: {} by {}", id, currentEmployee.getFullName());
        LeaveResponseDTO response = leaveService.rejectLeave(id, rejectionReason, currentEmployee.getId(), currentEmployee.getFullName());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // LEAVE POLICIES MANAGEMENT
    // =====================================================

    @GetMapping("/leaves/policies")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_VIEW')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> getLeavePolicies(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get leave policies for tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> policies = leaveConfigService.getLeavePolicies(tenantId);
        return ResponseEntity.ok(policies);
    }

    @PutMapping("/leaves/policies/{leaveType}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<com.sonixhr.dto.leave.LeavePolicyDTO> updateLeavePolicy(
            @PathVariable String leaveType,
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update leave policy for {} under tenant: {}", leaveType, tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, leaveType, policyUpdate);
        return ResponseEntity.ok(updatedPolicies.get(leaveType.toUpperCase()));
    }
}
