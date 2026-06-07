package com.sonixhr.dto.leave;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveApproveRequestDTO {

    @NotNull(message = "Leave ID is required")
    private Long leaveId;

    private String comments;
}