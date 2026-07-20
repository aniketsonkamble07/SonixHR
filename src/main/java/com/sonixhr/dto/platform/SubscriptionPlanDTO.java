package com.sonixhr.dto.platform;

import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be zero or positive")
    @DecimalMax(value = "999999.99", message = "Price cannot exceed 999999.99")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Min(value = 1, message = "Validity months must be at least 1")
    private int validityMonths;

    private boolean isActive;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @PositiveOrZero(message = "Max employees must be zero or positive")
    private Integer maxEmployees;
    @Column(name = "max_users")
    @Positive(message = "Max users must be positive")
    private Integer maxUsers;

    private java.util.List<String> enabledFeatures;
}
