package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.SupportTicket;
import com.sonixhr.security.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportTicketRepository extends TenantAwareRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    long countByStatus(String status);

    @Query("SELECT s FROM SupportTicket s WHERE " +
           "(CAST(:tenantName AS string) IS NULL OR LOWER(s.tenant.companyName) LIKE LOWER(CONCAT('%', CAST(:tenantName AS string), '%'))) AND " +
           "(CAST(:status AS string) IS NULL OR s.status = :status) AND " +
           "(CAST(:priority AS string) IS NULL OR s.priority = :priority)")
    Page<SupportTicket> searchTickets(@Param("tenantName") String tenantName,
                                      @Param("status") String status,
                                      @Param("priority") String priority,
                                      Pageable pageable);

    @Query("SELECT s FROM SupportTicket s WHERE s.tenant.id = :tenantId AND " +
           "(CAST(:status AS string) IS NULL OR s.status = :status)")
    Page<SupportTicket> searchTenantTickets(@Param("tenantId") Long tenantId,
                                            @Param("status") String status,
                                            Pageable pageable);
}
