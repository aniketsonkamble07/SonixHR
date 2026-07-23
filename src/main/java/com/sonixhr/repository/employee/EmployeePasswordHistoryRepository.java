// repository/employee/EmployeePasswordHistoryRepository.java
package com.sonixhr.repository.employee;

import com.sonixhr.entity.employee.EmployeePasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeePasswordHistoryRepository extends JpaRepository<EmployeePasswordHistory, Long> {

    @Query("SELECT ph FROM EmployeePasswordHistory ph WHERE ph.employeeId = :employeeId ORDER BY ph.createdAt DESC")
    List<EmployeePasswordHistory> findByEmployeeIdOrderByCreatedAtDesc(@Param("employeeId") Long employeeId);

    @Query("SELECT ph FROM EmployeePasswordHistory ph WHERE ph.employeeId = :employeeId AND ph.passwordHash = :passwordHash")
    Optional<EmployeePasswordHistory> findByEmployeeIdAndPasswordHash(@Param("employeeId") Long employeeId,
                                                                      @Param("passwordHash") String passwordHash);

    @Query("SELECT COUNT(ph) FROM EmployeePasswordHistory ph WHERE ph.employeeId = :employeeId")
    long countByEmployeeId(@Param("employeeId") Long employeeId);

    // Get last N password hashes for a employee
    @Query(value = "SELECT ph.password_hash FROM employee_password_history ph " +
            "WHERE ph.employee_id = :employeeId " +
            "ORDER BY ph.created_at DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<String> findLastNPasswordHashes(@Param("employeeId") Long employeeId,
                                         @Param("limit") int limit);

    // repository/employee/EmployeePasswordHistoryRepository.java

    @Modifying
    @Query(value = "DELETE FROM employee_password_history ph " +
            "WHERE ph.employee_id = :employeeId " +
            "AND ph.id NOT IN (" +
            "   SELECT id FROM employee_password_history " +
            "   WHERE employee_id = :employeeId " +
            "   ORDER BY created_at DESC " +
            "   LIMIT :keepCount" +
            ")", nativeQuery = true)
    default void deleteOldEntries(@Param("employeeId") Long employeeId,
                                  @Param("keepCount") int keepCount) {
        // Check if there are any entries first
        long count = countByEmployeeId(employeeId);
        if (count <= keepCount) {
            return; // No need to delete if there are fewer entries than keepCount
        }
        // Execute the delete query
        deleteOldEntriesInternal(employeeId, keepCount);
    }

    // Add this method for the actual delete
    @Modifying
    @Query(value = "DELETE FROM employee_password_history ph " +
            "WHERE ph.employee_id = :employeeId " +
            "AND ph.id NOT IN (" +
            "   SELECT id FROM employee_password_history " +
            "   WHERE employee_id = :employeeId " +
            "   ORDER BY created_at DESC " +
            "   LIMIT :keepCount" +
            ")", nativeQuery = true)
    void deleteOldEntriesInternal(@Param("employeeId") Long employeeId,
                                  @Param("keepCount") int keepCount);
}