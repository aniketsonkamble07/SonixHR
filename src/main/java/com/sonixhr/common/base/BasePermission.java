package com.sonixhr.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BasePermission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String permission;

    @Column(length = 200)
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}