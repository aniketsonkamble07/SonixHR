package com.sonixhr.repository.attendance;

import com.sonixhr.entity.attendance.BiometricDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice, Long> {
    Optional<BiometricDevice> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<BiometricDevice> findByTenantId(UUID tenantId);
    Optional<BiometricDevice> findBySerialNumber(String serialNumber);
}