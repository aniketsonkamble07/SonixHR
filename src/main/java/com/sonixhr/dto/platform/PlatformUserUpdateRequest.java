package com.sonixhr.dto.platform;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserUpdateRequest {
    private String fullName;
    private String designation;
    private String email;
}