package com.sonixhr.controller.task;

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

    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_CREATE') or #currentEmployee.isManager() or #currentEmployee.isSuperAdmin()")
    public ResponseEntity<TaskResponseDTO> createTask(
            @Valid @RequestBody TaskCreateRequestDTO dto,
            @AuthenticationPrincipal Employee currentEmployee) {
        if (currentEmployee == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long assignerId = currentEmployee.getId();
        if (assignerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("REST request to create task: {} by {}", dto.getTitle(), currentEmployee.getFullName());
        TaskResponseDTO response = taskService.createTask(dto, assignerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_VIEW_OWN') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<Page<TaskResponseDTO>> getMyTasks(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {
        if (currentEmployee == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long employeeId = currentEmployee.getId();
        if (employeeId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (pageable == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("REST request to get own tasks for: {}", currentEmployee.getFullName());
        Page<TaskResponseDTO> response = taskService.getMyTasks(employeeId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_VIEW_ALL') or #currentEmployee.isManager() or #currentEmployee.isSuperAdmin()")
    public ResponseEntity<Page<TaskResponseDTO>> getAllTasks(
            @AuthenticationPrincipal Employee currentEmployee,
            @PageableDefault(size = 20) Pageable pageable) {
        if (currentEmployee == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("REST request to get all tenant tasks by: {}", currentEmployee.getFullName());
        Page<TaskResponseDTO> response = taskService.getAllTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_ACKNOWLEDGE') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> acknowledgeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        if (currentEmployee == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long employeeId = currentEmployee.getId();
        if (employeeId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("REST request to acknowledge task: {} by {}", id, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.acknowledgeTask(id, employeeId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication, 'TASK_UPDATE_STATUS') or hasAnyAuthority('EMPLOYEE')")
    public ResponseEntity<TaskResponseDTO> updateTaskStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal Employee currentEmployee) {
        if (id == null || status == null) {
            return ResponseEntity.badRequest().build();
        }
        if (currentEmployee == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long employeeId = currentEmployee.getId();
        if (employeeId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("REST request to update task: {} status to {} by {}", id, status, currentEmployee.getFullName());
        TaskResponseDTO response = taskService.updateTaskStatus(id, status, employeeId);
        return ResponseEntity.ok(response);
    }
}
