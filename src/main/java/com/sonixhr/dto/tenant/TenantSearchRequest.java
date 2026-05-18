package com.sonixhr.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSearchRequest {

    private String searchTerm;
    private String status;
    private String planType;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;

    private String sortBy;

    @Builder.Default
    private String sortDirection = "ASC";
}