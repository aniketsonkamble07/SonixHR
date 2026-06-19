package com.sonixhr.dto.task;

import com.sonixhr.enums.task.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponseDTO {

    private Long id;
    private String title;
    private String description;
    private Long assignedToId;
    private String assignedToName;
    private Long assignedById;
    private String assignedByName;
    private LocalDate dueDate;
    private TaskStatus status;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime completedAt;
    private String declineReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
