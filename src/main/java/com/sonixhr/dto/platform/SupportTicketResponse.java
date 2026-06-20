package com.sonixhr.dto.platform;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketResponse {
    private Long id;
    private String ticketNumber;
    private Long tenantId;
    private String tenantCompanyName;
    private String tenantCode;
    private Long raisedByEmployeeId;
    private String raisedByEmployeeName;
    private String raisedByEmployeeEmail;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String status;
    private String resolution;
    private Long resolvedByPlatformUserId;
    private String resolvedByPlatformUserName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
