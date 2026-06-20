package com.sonixhr.dto.platform;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformNotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String type;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
