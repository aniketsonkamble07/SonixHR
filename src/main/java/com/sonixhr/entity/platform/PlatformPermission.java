package com.sonixhr.entity.platform;

import com.sonixhr.enums.PlatformPermissionEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "platform_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_permission_name", columnNames = { "permission"})
        },
        indexes = {
                @Index(name = "idx_permission_type", columnList = "permission"),
                @Index(name = "idx_permission_category", columnList = "category")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PlatformPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PlatformPermissionEnum permission;

    @Column(length = 200)
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "display_order")
    private Integer displayOrder;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @Version
    private Long version;

    // ==================== Helper Methods ====================

    public String getPermissionName() {
        return permission != null ? permission.name() : null;
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformPermission that = (PlatformPermission) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PlatformPermission{" +
                "id=" + id +
                ", permission=" + permission +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", displayOrder=" + displayOrder +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy=" + createdBy +
                ", version=" + version +
                '}';
    }
}