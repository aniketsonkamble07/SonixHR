package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserStatistics {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long pendingVerification;
    private long suspendedUsers;
    private long lockedUsers;
}