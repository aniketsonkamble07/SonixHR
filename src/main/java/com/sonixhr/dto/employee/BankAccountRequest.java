package com.sonixhr.dto.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountRequest {

    @NotBlank(message = "Account holder name is required")
    @Size(max = 200)
    private String accountHolderName;

    @NotBlank(message = "Account number is required")
    @Size(max = 30)
    private String accountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code format (e.g. HDFC0001234)")
    private String ifscCode;

    @NotBlank(message = "Bank name is required")
    @Size(max = 200)
    private String bankName;

    @Size(max = 200)
    private String branchName;

    // SAVINGS or CURRENT
    private String accountType;

    @Builder.Default
    private boolean isPrimary = true;
}
