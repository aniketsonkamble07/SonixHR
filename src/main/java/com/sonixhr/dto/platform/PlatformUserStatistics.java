package com.sonixhr.dto.platform;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformUserStatistics {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long pendingVerification;
    private long lockedUsers;
}