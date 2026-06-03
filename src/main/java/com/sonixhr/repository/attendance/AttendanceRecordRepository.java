package com.sonixhr.repository.attendance;
import com.sonixhr.entity.attendance.AttendanceRecord;
import com.sonixhr.enums.attendance.AttendanceMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    // Basic queries
    Optional<AttendanceRecord> findByTenantIdAndEmployeeIdAndDate(UUID tenantId, Long employeeId, LocalDate date);

    Page<AttendanceRecord> findByTenantIdAndEmployeeId(UUID tenantId, Long employeeId, Pageable pageable);

    Page<AttendanceRecord> findByTenantId(UUID tenantId, Pageable pageable);

    List<AttendanceRecord> findByTenantIdAndEmployeeIdAndDateBetween(UUID tenantId, Long employeeId, LocalDate startDate, LocalDate endDate);

    // Date range queries
    Page<AttendanceRecord> findByTenantIdAndDateBetween(UUID tenantId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Status counts
    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.tenantId = :tenantId AND a.date = :date AND a.status IN ('PRESENT', 'LATE')")
    long countPresentByTenantIdAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.tenantId = :tenantId AND a.date = :date AND a.status = 'LATE'")
    long countLateByTenantIdAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.tenantId = :tenantId AND a.date = :date AND a.status = 'ABSENT'")
    long countAbsentByTenantIdAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    // Method specific
    List<AttendanceRecord> findByMethodAndDateBetween(AttendanceMethod method, LocalDate startDate, LocalDate endDate);

    // Biometric device specific
    List<AttendanceRecord> findByDeviceIdAndDateBetween(String deviceId, LocalDate startDate, LocalDate endDate);

    // Check if attendance exists
    boolean existsByTenantIdAndEmployeeIdAndDate(UUID tenantId, Long employeeId, LocalDate date);
}
