package com.sonixhr.controller.leave;

import com.sonixhr.dto.employee.EmployeeResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class LeaveManagementController {

    private final LeaveService leaveService;
    private final LeaveConfigurationService leaveConfigService;
    private final EmployeeService employeeService;

    // =====================================================
    // TENANT LEAVE SETTINGS
    // =====================================================

    @GetMapping("/leaves/settings")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_VIEW')")
    public ResponseEntity<TenantLeaveSettings> getTenantLeaveSettings(
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to get leave settings for tenant: {}", tenantId);
        TenantLeaveSettings settings = leaveConfigService.getTenantSettings(tenantId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/leaves/settings")
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
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_ANY') or @permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_DEPARTMENT') or #currentEmployee.isManager()")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to approve leave request: {} by {}", id, currentEmployee.getFullName());
        LeaveResponseDTO response = leaveService.approveLeave(id, currentEmployee.getId(), currentEmployee.getFullName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/leaves/{id}/reject")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_ANY') or @permissionEvaluator.hasPermission(authentication, 'LEAVE_APPROVE_DEPARTMENT') or #currentEmployee.isManager()")
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
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateLeavePolicy(
            @PathVariable String leaveType,
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update leave policy for {} under tenant: {}", leaveType, tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, leaveType, policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/casual")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateCasualPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update CASUAL leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "CASUAL", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/sick")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateSickPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update SICK leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "SICK", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/earned")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateEarnedPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update EARNED leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "EARNED", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/emergency")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateEmergencyPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update EMERGENCY leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "EMERGENCY", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/maternity")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateMaternityPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update MATERNITY leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "MATERNITY", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/paternity")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updatePaternityPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update PATERNITY leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "PATERNITY", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/unpaid")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateUnpaidPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update UNPAID leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "UNPAID", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }

    @PutMapping("/leaves/policies/compensatory")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'SETTINGS_EDIT')")
    public ResponseEntity<java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO>> updateCompensatoryPolicy(
            @RequestBody com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate,
            @AuthenticationPrincipal Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        log.info("REST request to update COMPENSATORY leave policy under tenant: {}", tenantId);
        java.util.Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updatedPolicies = leaveConfigService.updateLeavePolicy(tenantId, "COMPENSATORY", policyUpdate);
        return ResponseEntity.ok(updatedPolicies);
    }
}
