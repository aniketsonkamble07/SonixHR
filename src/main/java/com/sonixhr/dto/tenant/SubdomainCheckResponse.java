package com.sonixhr.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubdomainCheckResponse {
    private boolean available;
    private String message;
}