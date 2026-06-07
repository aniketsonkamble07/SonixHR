package com.sonixhr.dto.leave;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRejectRequestDTO {

    @NotNull(message = "Leave ID is required")
    private Long leaveId;

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;

    private String comments;
}