package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("leaveSecurity")
@RequiredArgsConstructor
public class LeaveSecurity {

    private final LeaveRequestRepository leaveRequestRepository;

    public boolean canApproveOrReject(Long leaveId, Employee currentEmployee) {
        if (leaveId == null || currentEmployee == null) {
            return false;
        }

        // Super Admin / Platform User or has LEAVE_APPROVE_ANY can do it
        if (currentEmployee.isSuperAdmin() || currentEmployee.hasPermission("LEAVE_APPROVE_ANY")) {
            return true;
        }

        LeaveRequest leave = leaveRequestRepository.findById(leaveId).orElse(null);
        if (leave == null) {
            log.warn("Leave request not found for id: {}", leaveId);
            return false;
        }

        // Verify same tenant
        if (!currentEmployee.getTenantId().equals(leave.getTenant().getId())) {
            return false;
        }

        Employee requester = leave.getEmployee();
        if (requester == null) {
            return false;
        }

        // Cannot approve own leave request
        if (requester.getId().equals(currentEmployee.getId())) {
            return false;
        }

        // Check if direct manager
        boolean isDirectManager = requester.getManager() != null && requester.getManager().getId().equals(currentEmployee.getId());
        if (isDirectManager) {
            return true;
        }

        // Check department approval permission
        boolean hasApproveDept = currentEmployee.hasPermission("LEAVE_APPROVE_DEPARTMENT") &&
                requester.getDepartment() != null &&
                currentEmployee.getDepartment() != null &&
                requester.getDepartment().getId().equals(currentEmployee.getDepartment().getId());
        if (hasApproveDept) {
            return true;
        }

        return false;
    }

    public boolean isLeaveOwner(Long leaveId, Employee currentEmployee) {
        if (leaveId == null || currentEmployee == null) {
            return false;
        }
        LeaveRequest leave = leaveRequestRepository.findById(leaveId).orElse(null);
        if (leave == null) {
            log.warn("Leave request not found for id: {}", leaveId);
            return false;
        }
        return leave.getEmployee().getId().equals(currentEmployee.getId());
    }
}
