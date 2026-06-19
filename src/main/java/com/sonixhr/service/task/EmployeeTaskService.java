package com.sonixhr.service.task;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeTaskService {

    private final EmployeeTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

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

        if (task == null) {
            throw new BusinessException("Failed to build employee task");
        }

        EmployeeTask saved = taskRepository.save(task);
        log.info("Task created successfully with ID: {}", saved.getId());
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
            // Only assigner, direct manager, or super admin can cancel a task
            if (!isAssigner && !isDirectManager && !isSuperAdmin) {
                throw new BusinessException("You are not authorized to cancel this task");
            }
        } else {
            // Assignee (or direct manager/super admin) can update to IN_PROGRESS or COMPLETED
            if (!isAssignee && !isDirectManager && !isSuperAdmin) {
                throw new BusinessException("You are not authorized to update this task status");
            }
            if (newStatus == TaskStatus.COMPLETED) {
                task.setCompletedAt(LocalDateTime.now());
            }
        }

        task.setStatus(newStatus);
        EmployeeTask saved = taskRepository.save(task);
        return convertToResponse(saved);
    }

    public Page<TaskResponseDTO> getMyTasks(@NonNull Long employeeId, @NonNull Pageable pageable) {
        Page<EmployeeTask> tasks = taskRepository.findByAssignedToId(employeeId, pageable);
        return tasks.map(this::convertToResponse);
    }

    public Page<TaskResponseDTO> getAllTasks(Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required");
        }
        Page<EmployeeTask> tasks = taskRepository.findByTenantId(tenantId, pageable);
        return tasks.map(this::convertToResponse);
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
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
