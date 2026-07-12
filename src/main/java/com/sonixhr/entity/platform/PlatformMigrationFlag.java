package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_migration_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformMigrationFlag {

    @Id
    @Column(name = "flag_key", nullable = false, unique = true)
    private String flagKey;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}
