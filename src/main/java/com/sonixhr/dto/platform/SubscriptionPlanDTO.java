package com.sonixhr.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {

    private Long id;

    @NotBlank(message = "Plan code is required")
    @Size(min = 2, max = 50, message = "Plan code must be between 2 and 50 characters")
    private String code;

    @NotBlank(message = "Plan name is required")
    @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
    private String name;

    @PositiveOrZero(message = "Price must be zero or positive")
    private java.math.BigDecimal price;

    @Positive(message = "Validity months must be a positive number")
    private int validityMonths;

    private boolean isActive;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
