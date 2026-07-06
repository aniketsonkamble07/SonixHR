package com.sonixhr.service.task;

import com.sonixhr.dto.task.TaskAssigneeDTO;
import com.sonixhr.dto.task.TaskCreateRequestDTO;
import com.sonixhr.dto.task.TaskResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.task.EmployeeTask;
import com.sonixhr.enums.task.TaskStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.task.EmployeeTaskRepository;
import com.sonixhr.security.TenantContext;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.leave.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class EmployeeTaskService {

    private final EmployeeTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Transactional
    public TaskResponseDTO createTask(@NonNull TaskCreateRequestDTO dto, @NonNull Long assignerId) {
        log.info("Creating task with title: {} assigned by: {}", dto.getTitle(), assignerId);

        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required");
        }

        Employee assigner = employeeRepository.findById(assignerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assigner employee not found"));

        Long assignedToId = dto.getAssignedToId();
        if (assignedToId == null) {
            throw new BusinessException("Assigned to employee ID is required");
        }

        if (assignedToId.equals(assignerId)) {
            throw new BusinessException("Cannot assign a task to yourself");
        }

        Employee assignee = employeeRepository.findById(assignedToId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee employee not found"));

        // Verify tenant boundaries
        if (!assignee.getTenantId().equals(tenantId) || !assigner.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employees must belong to the same tenant");
        }

        // Validate authority
        boolean isSuperAdmin = assigner.isSuperAdmin();
        boolean hasTaskCreatePermission = assigner.hasPermission("TASK_CREATE");
        boolean isDirectManager = assignee.getManager() != null && assignee.getManager().getId().equals(assignerId);
        boolean isSameDepartmentManager = assigner.isManager() && 
                assigner.getDepartment() != null && 
                assignee.getDepartment() != null && 
                assigner.getDepartment().getId().equals(assignee.getDepartment().getId());

        if (!isSuperAdmin && !hasTaskCreatePermission && !isDirectManager && !isSameDepartmentManager) {
            throw new BusinessException("You are not authorized to assign tasks to this employee");
        }

        EmployeeTask task = EmployeeTask.builder()
                .tenant(assigner.getTenant())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .assignedTo(assignee)
                .assignedBy(assigner)
                .dueDate(dto.getDueDate())
                .status(TaskStatus.PENDING)
                .build();

        EmployeeTask saved = taskRepository.save(task);
        log.info("Task created successfully with ID: {}", saved.getId());

        // Send Email Notification to assignee
        if (assignee.getEmail() != null) {
            emailService.sendTaskNotification(
                    assignee.getEmail(),
                    assignee.getFullName(),
                    saved.getTitle(),
                    "ASSIGNED",
                    assigner.getFullName()
            );
        }

        // Send In-App Notification to assignee
        notificationService.sendNotification(
                assignee,
                "Task Assigned",
                String.format("You have been assigned a new task: '%s' by %s.", saved.getTitle(), assigner.getFullName()),
                "TASK"
        );

        return convertToResponse(saved);
    }

    @Transactional
    public TaskResponseDTO acknowledgeTask(@NonNull Long taskId, @NonNull Long employeeId) {
        log.info("Acknowledging task: {} by employee: {}", taskId, employeeId);

        EmployeeTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Verify tenant isolation
        Long tenantId = TenantContext.getCurrentTenant();
        if (!task.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        if (!task.getAssignedTo().getId().equals(employeeId)) {
            throw new BusinessException("You can only acknowledge tasks assigned to you");
        }

        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("Only pending tasks can be acknowledged");
        }

        task.setStatus(TaskStatus.ACKNOWLEDGED);
        task.setAcknowledgedAt(LocalDateTime.now());

        EmployeeTask saved = taskRepository.save(task);

        // Notify assigner that the task was acknowledged
        Employee assigner = saved.getAssignedBy();
        Employee assignee = saved.getAssignedTo();

        if (assigner.getEmail() != null) {
            emailService.sendTaskNotification(
                    assigner.getEmail(),
                    assigner.getFullName(),
                    saved.getTitle(),
                    "ACKNOWLEDGED",
                    assignee.getFullName()
            );
        }

        notificationService.sendNotification(
                assigner,
                "Task Acknowledged",
                String.format("Task '%s' was acknowledged by %s.", saved.getTitle(), assignee.getFullName()),
                "TASK"
        );

        return convertToResponse(saved);
    }

    @Transactional
    public TaskResponseDTO updateTaskStatus(@NonNull Long taskId, @NonNull TaskStatus newStatus, @NonNull Long employeeId) {
        log.info("Updating status of task: {} to {} by employee: {}", taskId, newStatus, employeeId);

        EmployeeTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Verify tenant isolation
        Long tenantId = TenantContext.getCurrentTenant();
        if (!task.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        Employee caller = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        boolean isAssignee = task.getAssignedTo().getId().equals(employeeId);
        boolean isAssigner = task.getAssignedBy().getId().equals(employeeId);
        boolean isSuperAdmin = caller.isSuperAdmin();
        boolean isDirectManager = task.getAssignedTo().getManager() != null && 
                task.getAssignedTo().getManager().getId().equals(employeeId);

        if (newStatus == TaskStatus.CANCELLED) {
            if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.DECLINED) {
                throw new BusinessException("Cannot cancel a task that is completed, declined, or already cancelled");
            }
            // Only assigner, direct manager, or super admin can cancel a task
            if (!isAssigner && !isDirectManager && !isSuperAdmin) {
                throw new BusinessException("You are not authorized to cancel this task");
            }
        } else if (newStatus == TaskStatus.IN_PROGRESS) {
            if (task.getStatus() != TaskStatus.ACCEPTED) {
                throw new BusinessException("Task must be accepted before starting work");
            }
            if (!isAssignee && !isSuperAdmin) {
                throw new BusinessException("You are not authorized to update this task status");
            }
        } else if (newStatus == TaskStatus.COMPLETED) {
            if (task.getStatus() != TaskStatus.ACCEPTED && task.getStatus() != TaskStatus.IN_PROGRESS) {
                throw new BusinessException("Task must be accepted or in progress before completing");
            }
            if (!isAssignee && !isSuperAdmin) {
                throw new BusinessException("You are not authorized to update this task status");
            }
            task.setCompletedAt(LocalDateTime.now());
        } else {
            throw new BusinessException("Invalid status transition via this endpoint. Use accept/decline/acknowledge endpoints instead.");
        }

        task.setStatus(newStatus);
        EmployeeTask saved = taskRepository.save(task);
        return convertToResponse(saved);
    }

    @Transactional
    public TaskResponseDTO acceptTask(@NonNull Long taskId, @NonNull Long employeeId) {
        log.info("Accepting task: {} by employee: {}", taskId, employeeId);

        EmployeeTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Verify tenant isolation
        Long tenantId = TenantContext.getCurrentTenant();
        if (!task.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        if (!task.getAssignedTo().getId().equals(employeeId)) {
            throw new BusinessException("You can only accept tasks assigned to you");
        }

        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.ACKNOWLEDGED) {
            throw new BusinessException("Only pending or acknowledged tasks can be accepted");
        }

        task.setStatus(TaskStatus.ACCEPTED);
        task.setAcceptedAt(LocalDateTime.now());
        EmployeeTask saved = taskRepository.save(task);

        // Send notifications to assigner
        Employee assigner = task.getAssignedBy();
        Employee assignee = task.getAssignedTo();

        // Email Notification
        if (assigner.getEmail() != null) {
            emailService.sendTaskNotification(
                    assigner.getEmail(),
                    assigner.getFullName(),
                    task.getTitle(),
                    "ACCEPTED",
                    assignee.getFullName()
            );
        }

        // In-App Notification
        notificationService.sendNotification(
                assigner,
                "Task Accepted",
                String.format("Task '%s' was accepted by %s.", task.getTitle(), assignee.getFullName()),
                "TASK"
        );

        return convertToResponse(saved);
    }

    @Transactional
    public TaskResponseDTO declineTask(@NonNull Long taskId, @NonNull String declineReason, @NonNull Long employeeId) {
        log.info("Declining task: {} by employee: {} with reason: {}", taskId, employeeId, declineReason);

        if (declineReason == null || declineReason.trim().isEmpty()) {
            throw new BusinessException("Decline reason is required");
        }

        EmployeeTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Verify tenant isolation
        Long tenantId = TenantContext.getCurrentTenant();
        if (!task.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        if (!task.getAssignedTo().getId().equals(employeeId)) {
            throw new BusinessException("You can only decline tasks assigned to you");
        }

        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.ACKNOWLEDGED) {
            throw new BusinessException("Only pending or acknowledged tasks can be declined");
        }

        task.setStatus(TaskStatus.DECLINED);
        task.setDeclineReason(declineReason);
        EmployeeTask saved = taskRepository.save(task);

        // Send notifications to assigner
        Employee assigner = task.getAssignedBy();
        Employee assignee = task.getAssignedTo();

        // Email Notification
        if (assigner.getEmail() != null) {
            emailService.sendTaskNotification(
                    assigner.getEmail(),
                    assigner.getFullName(),
                    task.getTitle(),
                    "DECLINED",
                    assignee.getFullName()
            );
        }

        // In-App Notification
        notificationService.sendNotification(
                assigner,
                "Task Declined",
                String.format("Task '%s' was declined by %s. Reason: %s", task.getTitle(), assignee.getFullName(), declineReason),
                "TASK"
        );

        return convertToResponse(saved);
    }

    public Page<TaskResponseDTO> getMyTasks(@NonNull Long employeeId, @NonNull Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required");
        }
        Page<EmployeeTask> tasks = taskRepository.findByTenantIdAndAssignedToId(tenantId, employeeId, pageable);
        return tasks.map(this::convertToResponse);
    }

    public Page<TaskResponseDTO> getAllTasks(@NonNull Long callerId, Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required");
        }

        Employee caller = employeeRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        Page<EmployeeTask> tasks;
        if (caller.isSuperAdmin() || caller.hasPermission("TASK_VIEW_ALL")) {
            tasks = taskRepository.findByTenantId(tenantId, pageable);
        } else if (caller.isManager() && caller.getDepartment() != null) {
            tasks = taskRepository.findByTenantIdAndAssignedToDepartmentId(tenantId, caller.getDepartment().getId(), pageable);
        } else {
            tasks = taskRepository.findByTenantId(tenantId, pageable);
        }

        return tasks.map(this::convertToResponse);
    }

    public List<TaskAssigneeDTO> getAssignableEmployees(@NonNull Long callerId, String query, int size) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required");
        }

        Employee caller = employeeRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        String safeQuery = (query == null) ? "" : query.trim();
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, Math.min(size, 100));

        List<Employee> employees;
        if (caller.isSuperAdmin() || caller.hasPermission("TASK_CREATE")) {
            employees = employeeRepository.searchAssignableEmployees(tenantId, callerId, safeQuery, pageable);
        } else if (caller.isManager() && caller.getDepartment() != null) {
            employees = employeeRepository.searchAssignableEmployeesForManager(
                    tenantId, caller.getDepartment().getId(), callerId, callerId, safeQuery, pageable);
        } else if (caller.isManager()) {
            employees = employeeRepository.searchAssignableDirectReports(tenantId, callerId, callerId, safeQuery, pageable);
        } else {
            employees = List.of();
        }

        return employees.stream().map(this::toTaskAssigneeDTO).collect(Collectors.toList());
    }

    private TaskAssigneeDTO toTaskAssigneeDTO(Employee e) {
        return TaskAssigneeDTO.builder()
                .id(e.getId())
                .fullName(e.getFullName())
                .employeeCode(e.getEmployeeCode())
                .departmentName(e.getDepartment() != null ? e.getDepartment().getName() : null)
                .designation(e.getPosition())
                .build();
    }

    private TaskResponseDTO convertToResponse(EmployeeTask task) {
        if (task == null) return null;
        return TaskResponseDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .assignedToId(task.getAssignedTo().getId())
                .assignedToName(task.getAssignedTo().getFullName())
                .assignedById(task.getAssignedBy().getId())
                .assignedByName(task.getAssignedBy().getFullName())
                .dueDate(task.getDueDate())
                .status(task.getStatus())
                .acknowledgedAt(task.getAcknowledgedAt())
                .acceptedAt(task.getAcceptedAt())
                .completedAt(task.getCompletedAt())
                .declineReason(task.getDeclineReason())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
