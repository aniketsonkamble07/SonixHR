package com.sonixhr.controller.leave;

import com.sonixhr.dto.leave.LeaveRequestDTO;
import com.sonixhr.dto.leave.LeaveResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.service.leave.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/employee/leaves")
@RequiredArgsConstructor
public class EmployeeLeaveController {

    private final LeaveService leaveService;

    @PostMapping
    public ResponseEntity<LeaveResponseDTO> requestLeave(
            @Valid @RequestBody LeaveRequestDTO request,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to request leave for employee: {}", currentEmployee.getId());
        LeaveResponseDTO response = leaveService.requestLeaveWithTenantSettings(currentEmployee.getId(), request, currentEmployee);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<LeaveResponseDTO>> getMyLeaves(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get own leaves for employee: {}", currentEmployee.getId());
        List<LeaveResponseDTO> response = leaveService.getMyLeaves(currentEmployee.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getLeaveBalance(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get leave balance for employee: {}", currentEmployee.getId());
        Map<String, Object> balance = leaveService.getLeaveBalanceWithTenantSettings(currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok(balance);
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
}
