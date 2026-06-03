package com.sonixhr.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight DTO for listing shift configurations
 * Used in dropdowns and summary tables
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConfigurationSummaryDTO {

    private Long id;
    private String shiftName;
    private String shiftCode;
    private String shiftTimeDisplay;
    private String shiftDurationDisplay;
    private Boolean isActive;
    private Boolean isDefault;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime updatedAt;

    /**
     * Create from full DTO
     */
    public static ShiftConfigurationSummaryDTO fromFullDTO(ShiftConfigurationDTO fullDTO) {
        return ShiftConfigurationSummaryDTO.builder()
                .id(fullDTO.getId())
                .shiftName(fullDTO.getShiftName())
                .shiftCode(fullDTO.getShiftCode())
                .shiftTimeDisplay(fullDTO.getShiftTimeFormatted())
                .shiftDurationDisplay(fullDTO.getShiftDurationFormatted())
                .isActive(fullDTO.getIsActive())
                .isDefault(fullDTO.getIsDefault())
                .effectiveFrom(fullDTO.getEffectiveFrom())
                .effectiveTo(fullDTO.getEffectiveTo())
                .updatedAt(fullDTO.getUpdatedAt())
                .build();
    }
}
