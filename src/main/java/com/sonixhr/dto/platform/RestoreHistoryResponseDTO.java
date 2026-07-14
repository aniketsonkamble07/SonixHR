package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreHistoryResponseDTO {

    private Long id;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
    private String performedByEmail;
    private String notes;
    private String planName;
}
