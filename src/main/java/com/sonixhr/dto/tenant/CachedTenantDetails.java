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

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public boolean getIsActive() { return isActive; }
    public void setIsActive(boolean isActive) { this.isActive = isActive; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public PlanStatus getPlanStatus() { return planStatus; }
    public void setPlanStatus(PlanStatus planStatus) { this.planStatus = planStatus; }
    public LocalDateTime getBillingPeriodEnd() { return billingPeriodEnd; }
    public void setBillingPeriodEnd(LocalDateTime billingPeriodEnd) { this.billingPeriodEnd = billingPeriodEnd; }
    public com.sonixhr.enums.TenantDataStatus getDataStatus() { return dataStatus; }
    public void setDataStatus(com.sonixhr.enums.TenantDataStatus dataStatus) { this.dataStatus = dataStatus; }
}
