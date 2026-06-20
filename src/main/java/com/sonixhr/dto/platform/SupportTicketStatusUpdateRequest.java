package com.sonixhr.dto.platform;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String resolution;
}
