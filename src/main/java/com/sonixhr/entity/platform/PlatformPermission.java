package com.sonixhr.entity.platform;

import com.sonixhr.enums.AdminPermission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "platform_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private AdminPermission name;

    private String description;
}