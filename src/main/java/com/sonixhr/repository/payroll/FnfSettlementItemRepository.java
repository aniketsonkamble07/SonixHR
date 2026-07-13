package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.FnfSettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FnfSettlementItemRepository extends JpaRepository<FnfSettlementItem, UUID> {
    List<FnfSettlementItem> findByFnfSettlementId(UUID fnfSettlementId);
}
