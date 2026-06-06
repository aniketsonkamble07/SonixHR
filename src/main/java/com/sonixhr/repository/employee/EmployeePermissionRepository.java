package com.sonixhr.repository.employee;

import com.sonixhr.entity.employee.EmployeePermission;
import com.sonixhr.enums.employee.EmployeePermissionEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {

    Optional<EmployeePermission> findByPermission(EmployeePermissionEnum permission);

    List<EmployeePermission> findByCategoryOrderByDisplayOrderAsc(String category);

    List<EmployeePermission> findAllByOrderByCategoryAscDisplayOrderAsc();
}