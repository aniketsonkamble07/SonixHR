package com.sonixhr.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveActionResponseDTO {

    private boolean success;
    private String message;
    private Long leaveId;
    private String action;
    private String performedBy;
    private LocalDateTime performedAt;
    private LeaveResponseDTO leaveDetails;
}