package com.sonixhr.service.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.events.EmployeeUpdatedEvent;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @EventListener
    @Transactional
    public void handleEmployeeUpdated(EmployeeUpdatedEvent event) {
        if ("DEACTIVATE".equalsIgnoreCase(event.getAction())) {
            log.info("Employee deactivated event received for ID: {}. Checking for seat reallocation.", event.getEmployeeId());
            reallocateFreeSeat(event.getEmployeeId());
        }
    }

    private void reallocateFreeSeat(Long deactivatedEmployeeId) {
        employeeRepository.findById(deactivatedEmployeeId).ifPresent(deactivatedEmployee -> {
            if (deactivatedEmployee.getTenant() == null) {
                return;
            }
            Long tenantId = deactivatedEmployee.getTenant().getId();
            
            // Find any invited/pending employee for this tenant (FIFO: oldest invitation date first)
            List<Employee> pendingInvites = employeeRepository.findPendingInvitedEmployeesOrderByCreatedAt(tenantId);
            if (!pendingInvites.isEmpty()) {
                Employee nextEmployee = pendingInvites.get(0);
                log.info("Reallocating seat from deactivated employee {} to pending employee {}", deactivatedEmployeeId, nextEmployee.getId());
                
                try {
                    // Send notification to admin / reissue invitation to user
                    emailService.sendActivationEmail(nextEmployee.getEmail(), nextEmployee.getFirstName() != null ? nextEmployee.getFirstName() : "Employee", "http://localhost:3000/activate");
                    log.info("Invitation re-sent successfully to pending employee {}", nextEmployee.getEmail());
                } catch (Exception e) {
                    log.error("Failed to reallocate seat notification for employee {}: {}", nextEmployee.getId(), e.getMessage());
                }
            } else {
                log.info("No pending invites found for tenant ID: {}. Seat freed but unallocated.", tenantId);
            }
        });
    }
}
