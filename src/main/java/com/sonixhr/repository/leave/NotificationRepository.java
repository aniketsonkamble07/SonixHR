package com.sonixhr.repository.leave;

import com.sonixhr.entity.leave.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByEmployeeIdAndTenantIdOrderByCreatedAtDesc(Long employeeId, Long tenantId);
}
