package com.sonixhr.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthDTO {

    private String databaseStatus;
    private String redisStatus;
    private String mailSenderStatus;
    private String diskSpaceStatus;
    private Long freeDiskSpaceBytes;
    private Long totalDiskSpaceBytes;
}
