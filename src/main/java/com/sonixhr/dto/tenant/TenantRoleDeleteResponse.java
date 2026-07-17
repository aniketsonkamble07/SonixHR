package com.sonixhr.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleDeleteResponse {
    private boolean deleted;
    private boolean requiresConfirmation;
    private String employeeName;
    private String message;
}
