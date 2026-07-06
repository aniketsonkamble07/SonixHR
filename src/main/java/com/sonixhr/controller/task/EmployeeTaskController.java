package com.sonixhr.controller.task;

import com.sonixhr.dto.task.TaskAssigneeDTO;
import com.sonixhr.dto.task.TaskCreateRequestDTO;
import com.sonixhr.dto.task.TaskResponseDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.enums.task.TaskStatus;
import com.sonixhr.service.task.EmployeeTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/employees/tasks")
@RequiredArgsConstructor
public class EmployeeTaskController {

    private final EmployeeTaskService taskService;

    @GetMapping("/assignees")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_CREATE') or #currentEmployee.isManager() or #currentEmployee.isSuperAdmin()")
    public ResponseEntity<List<TaskAssigneeDTO>> getAssignableEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get assignable employees by: {}", currentEmployee.getFullName());
        List<TaskAssigneeDTO> result = taskService.getAssignableEmployees(currentEmployee.getId(), q, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_CREATE') or #currentEmployee.isManager() or #currentEmployee.isSuperAdmin()")
    public ResponseEntity<TaskResponseDTO> createTask(
            @Valid @RequestBody TaskCreateRequestDTO dto,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to create task: {} by {}", dto.getTitle(), currentEmployee.getFullName());
        TaskResponseDTO response = taskService.createTask(dto, currentEmployee.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_VIEW_OWN') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<Page<TaskResponseDTO>> getMyTasks(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to get own tasks for: {}", currentEmployee.getFullName());
        Page<TaskResponseDTO> response = taskService.getMyTasks(currentEmployee.getId(), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_VIEW_ALL') or #currentEmployee.isManager() or #currentEmployee.isSuperAdmin()")
    public ResponseEntity<Page<TaskResponseDTO>> getAllTasks(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to get all tenant tasks by: {}", currentEmployee.getFullName());
        Page<TaskResponseDTO> response = taskService.getAllTasks(currentEmployee.getId(), pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_ACKNOWLEDGE') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> acknowledgeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to acknowledge task: {} by {}", id, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.acknowledgeTask(id, currentEmployee.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_UPDATE_STATUS') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> updateTaskStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to update task: {} status to {} by {}", id, status, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.updateTaskStatus(id, status, currentEmployee.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_UPDATE_STATUS') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> acceptTask(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to accept task: {} by {}", id, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.acceptTask(id, currentEmployee.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/decline")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_UPDATE_STATUS') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> declineTask(
            @PathVariable Long id,
            @RequestParam String declineReason,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to decline task: {} with reason: {} by {}", id, declineReason, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.declineTask(id, declineReason, currentEmployee.getId());
        return ResponseEntity.ok(response);
    }
}
