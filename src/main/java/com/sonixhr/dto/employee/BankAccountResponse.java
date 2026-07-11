package com.sonixhr.dto.employee;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankAccountResponse {
    private Long id;
    private String accountHolderName;
    private String maskedAccountNumber; // e.g. "XXXX1234"
    private String ifscCode;
    private String bankName;
    private String branchName;
    private String accountType;
    private boolean isPrimary;
    private boolean isActive;
}
