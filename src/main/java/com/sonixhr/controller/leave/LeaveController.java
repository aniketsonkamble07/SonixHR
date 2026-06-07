package com.sonixhr.controller.leave;

import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.leave.LeaveStatus;
import com.sonixhr.service.leave.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    // =====================================================
    // EMPLOYEE ENDPOINTS
    // =====================================================

    /**
     * Request a new leave
     */
    @PostMapping("/request")
    @PreAuthorize("hasAnyAuthority('REQUEST_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponseDTO> requestLeave(
            @Valid @RequestBody LeaveRequestDTO request,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Employee {} requesting leave", currentUser.getEmail());
        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(
                currentUser.getId(), request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get my leave requests
     */
    @GetMapping("/my-leaves")
    @PreAuthorize("hasAnyAuthority('VIEW_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getMyLeaves(
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Getting my leaves for employee: {}", currentUser.getEmail());
        List<LeaveResponseDTO> leaves = leaveService.getMyLeaves(currentUser.getId());
        return ResponseEntity.ok(leaves);
    }

    /**
     * Get my leave balance
     */
    @GetMapping("/my-balance")
    @PreAuthorize("hasAnyAuthority('VIEW_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMyLeaveBalance(
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Getting leave balance for employee: {}", currentUser.getEmail());
        Map<String, Object> balance = leaveService.getLeaveBalanceWithTenantSettings(
                currentUser.getId(), currentUser.getTenantId());
        return ResponseEntity.ok(balance);
    }

    /**
     * Cancel my leave request
     */
    @DeleteMapping("/{leaveId}/cancel")
    @PreAuthorize("hasAnyAuthority('CANCEL_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponseDTO> cancelLeave(
            @PathVariable Long leaveId,
            @RequestParam String reason,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Employee {} cancelling leave: {}", currentUser.getEmail(), leaveId);
        LeaveResponseDTO response = leaveService.cancelLeave(leaveId, currentUser.getId(), reason);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // MANAGER ENDPOINTS
    // =====================================================

    /**
     * Get team leave requests (pending by default)
     */
    @GetMapping("/team")
    @PreAuthorize("hasAnyAuthority('MANAGE_TEAM_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<Page<LeaveResponseDTO>> getTeamLeaveRequests(
            @RequestParam(required = false) LeaveStatus status,
            @AuthenticationPrincipal Employee currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Manager {} getting team leave requests", currentUser.getEmail());
        Page<LeaveResponseDTO> leaves = leaveService.getTeamLeaveRequests(
                currentUser.getId(), currentUser.getTenantId(), status, pageable);
        return ResponseEntity.ok(leaves);
    }

    /**
     * Approve a leave request
     */
    @PostMapping("/{leaveId}/approve")
    @PreAuthorize("hasAnyAuthority('APPROVE_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Manager {} approving leave: {}", currentUser.getEmail(), leaveId);
        LeaveResponseDTO response = leaveService.approveLeave(
                leaveId, currentUser.getId(), currentUser.getFullName());
        return ResponseEntity.ok(response);
    }

    /**
     * Reject a leave request
     */
    @PostMapping("/{leaveId}/reject")
    @PreAuthorize("hasAnyAuthority('REJECT_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponseDTO> rejectLeave(
            @PathVariable Long leaveId,
            @RequestParam String reason,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Manager {} rejecting leave: {}", currentUser.getEmail(), leaveId);
        LeaveResponseDTO response = leaveService.rejectLeave(
                leaveId, reason, currentUser.getId(), currentUser.getFullName());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // CALENDAR ENDPOINTS
    // =====================================================

    /**
     * Get approved leaves for calendar view
     */
    @GetMapping("/calendar")
    @PreAuthorize("hasAnyAuthority('VIEW_LEAVE', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getLeavesForCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long employeeId,
            @AuthenticationPrincipal Employee currentUser) {

        Long targetEmployeeId = employeeId != null ? employeeId : currentUser.getId();

        log.info("Getting leaves for calendar for employee: {} for {}-{}", targetEmployeeId, year, month);

        List<LeaveResponseDTO> leaves = leaveService.getApprovedLeavesForCalendar(
                targetEmployeeId, currentUser.getTenantId(), year, month);

        return ResponseEntity.ok(leaves);
    }

    // =====================================================
    // ADMIN ENDPOINTS
    // =====================================================

    /**
     * Get all leave requests for tenant (Admin only)
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Page<LeaveResponseDTO>> getAllLeaveRequests(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal Employee currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Admin {} getting all leave requests", currentUser.getEmail());

        // Implementation for admin to get all leaves with filters
        // You can add this method to LeaveService if needed
        return ResponseEntity.ok(Page.empty());
    }

    /**
     * Get leave balance for any employee (Admin only)
     */
    @GetMapping("/admin/balance/{employeeId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getEmployeeLeaveBalance(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Employee currentUser) {

        log.info("Admin {} getting leave balance for employee: {}", currentUser.getEmail(), employeeId);

        Map<String, Object> balance = leaveService.getLeaveBalanceWithTenantSettings(
                employeeId, currentUser.getTenantId());
        return ResponseEntity.ok(balance);
    }
}