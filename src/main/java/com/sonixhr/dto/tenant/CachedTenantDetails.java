package com.sonixhr.dto.tenant;

import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedTenantDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private boolean isActive;
    private UserStatus status;
    private PlanStatus planStatus;
    private LocalDateTime billingPeriodEnd;
    private com.sonixhr.enums.TenantDataStatus dataStatus;
}
