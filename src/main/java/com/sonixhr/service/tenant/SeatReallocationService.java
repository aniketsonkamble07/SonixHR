package com.sonixhr.service.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.events.EmployeeUpdatedEvent;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatReallocationService {

    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Handle employee updated events to reallocate seats when employees are deactivated
     */
    @EventListener
    @Transactional
    public void handleEmployeeUpdated(EmployeeUpdatedEvent event) {
        log.info("Employee updated event received - Employee ID: {}, Email: {}, Action: {}",
                event.getEmployeeId(), event.getEmail(), event.getAction());

        // Check if this is a deactivation event
        if (isDeactivationEvent(event)) {
            log.info("Employee deactivation detected. Checking for seat reallocation. Employee ID: {}",
                    event.getEmployeeId());
            reallocateFreeSeat(event.getEmployeeId());
        }
    }

    /**
     * Reallocate a free seat to a pending employee (FIFO - oldest pending first)
     */
    @Transactional
    public void reallocateFreeSeat(Long deactivatedEmployeeId) {
        if (deactivatedEmployeeId == null) {
            log.warn("Cannot reallocate seat: deactivated employee ID is null");
            return;
        }

        employeeRepository.findById(deactivatedEmployeeId).ifPresentOrElse(
                deactivatedEmployee -> {
                    Tenant tenant = deactivatedEmployee.getTenant();
                    if (tenant == null) {
                        log.warn("Deactivated employee {} has no tenant. Cannot reallocate seat.",
                                deactivatedEmployeeId);
                        return;
                    }

                    Long tenantId = tenant.getId();
                    log.info("Checking for pending employees for tenant ID: {}", tenantId);

                    // Find any invited/pending employee for this tenant (FIFO: oldest invitation date first)
                    List<Employee> pendingInvites = employeeRepository.findPendingInvitedEmployeesOrderByCreatedAt(tenantId);

                    if (!pendingInvites.isEmpty()) {
                        Employee nextEmployee = pendingInvites.get(0);
                        log.info("Reallocating seat from deactivated employee {} to pending employee {} (Email: {})",
                                deactivatedEmployeeId, nextEmployee.getId(), nextEmployee.getEmail());

                        try {
                            // Generate activation link
                            String activationLink = frontendUrl + "/auth/activate?email=" + nextEmployee.getEmail();

                            // Send invitation email
                            String employeeName = nextEmployee.getFirstName() != null ?
                                    nextEmployee.getFirstName() : "Employee";

                            emailService.sendActivationEmail(
                                    nextEmployee.getEmail(),
                                    employeeName,
                                    activationLink
                            );

                            log.info("Invitation re-sent successfully to pending employee: {}", nextEmployee.getEmail());

                            // Optional: Update employee status to indicate invitation was resent
                            // nextEmployee.setInvitationResentAt(LocalDateTime.now());
                            // employeeRepository.save(nextEmployee);

                        } catch (Exception e) {
                            log.error("Failed to reallocate seat notification for employee {}: {}",
                                    nextEmployee.getId(), e.getMessage(), e);
                        }
                    } else {
                        log.info("No pending invites found for tenant ID: {}. Seat freed but unallocated.", tenantId);
                    }
                },
                () -> log.warn("Deactivated employee with ID {} not found. Cannot reallocate seat.",
                        deactivatedEmployeeId)
        );
    }

    /**
     * Check if the event is a deactivation event
     */
    private boolean isDeactivationEvent(EmployeeUpdatedEvent event) {
        if (event == null) {
            return false;
        }

        // Check by action type
        if ("DEACTIVATE".equalsIgnoreCase(event.getAction())) {
            return true;
        }

        // Check by event type - handle both "DEACTIVATE" and "STATUS_CHANGE" with deactivation
        if ("STATUS_CHANGE".equalsIgnoreCase(event.getAction())) {
            // If we have additional info, we could check if the status changed to inactive
            // For now, assume it might be a deactivation
            return true;
        }

        return false;
    }

    /**
     * Manually trigger seat reallocation for a tenant (admin API)
     */
    @Transactional
    public int reallocateAllAvailableSeats(Long tenantId) {
        log.info("Manually reallocating all available seats for tenant ID: {}", tenantId);

        if (tenantId == null) {
            log.warn("Cannot reallocate seats: tenant ID is null");
            return 0;
        }

        // Find all deactivated employees for this tenant
        List<Employee> deactivatedEmployees = employeeRepository.findByTenantIdAndStatus(
                tenantId, EmployeeStatus.TERMINATED);

        // Find all pending employees
        List<Employee> pendingEmployees = employeeRepository.findPendingInvitedEmployeesOrderByCreatedAt(tenantId);

        int reallocatedCount = 0;

        // For each deactivated employee, try to reallocate to a pending employee
        for (Employee deactivated : deactivatedEmployees) {
            if (!pendingEmployees.isEmpty()) {
                Employee nextEmployee = pendingEmployees.remove(0);
                try {
                    String activationLink = frontendUrl + "/auth/activate?email=" + nextEmployee.getEmail();
                    String employeeName = nextEmployee.getFirstName() != null ?
                            nextEmployee.getFirstName() : "Employee";

                    emailService.sendActivationEmail(
                            nextEmployee.getEmail(),
                            employeeName,
                            activationLink
                    );

                    reallocatedCount++;
                    log.info("Reallocated seat from {} to {}",
                            deactivated.getEmployeeCode(), nextEmployee.getEmployeeCode());
                } catch (Exception e) {
                    log.error("Failed to reallocate seat for employee {}: {}",
                            nextEmployee.getId(), e.getMessage(), e);
                }
            } else {
                break;
            }
        }

        log.info("Reallocated {} seats for tenant ID: {}", reallocatedCount, tenantId);
        return reallocatedCount;
    }

    /**
     * Get count of available seats for a tenant
     */
    @Transactional(readOnly = true)
    public int getAvailableSeatCount(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }

        long deactivatedCount = employeeRepository.countByTenantIdAndStatus(
                tenantId, EmployeeStatus.TERMINATED);
        long pendingCount = employeeRepository.countPendingInvitedEmployees(tenantId);

        // Available seats = deactivated employees - pending employees (if any)
        return (int) Math.max(0, deactivatedCount - pendingCount);
    }
}