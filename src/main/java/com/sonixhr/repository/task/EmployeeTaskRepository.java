package com.sonixhr.repository.task;

import com.sonixhr.entity.task.EmployeeTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeTaskRepository extends JpaRepository<EmployeeTask, Long> {
    Page<EmployeeTask> findByTenantIdAndAssignedToId(Long tenantId, Long employeeId, Pageable pageable);
    Page<EmployeeTask> findByTenantId(Long tenantId, Pageable pageable);
    Page<EmployeeTask> findByTenantIdAndAssignedToDepartmentId(Long tenantId, Long departmentId, Pageable pageable);
}
