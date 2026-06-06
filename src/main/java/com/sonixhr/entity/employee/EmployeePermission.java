package com.sonixhr.entity.employee;

import com.sonixhr.enums.employee.EmployeePermissionEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employee_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false, length = 100)
    private EmployeePermissionEnum permission;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String category;

    private Integer displayOrder;

    @PrePersist
    @PreUpdate
    public void populateFields() {
        if (permission != null) {
            if (description == null) {
                this.description = permission.getDescription();
            }
            if (category == null) {
                this.category = permission.getCategory();
            }
            if (displayOrder == null) {
                this.displayOrder = permission.getOrder();
            }
        }
    }
}