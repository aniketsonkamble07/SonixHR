package com.sonixhr.repository.payroll;

import com.sonixhr.entity.payroll.PayrunConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrunConfigRepository extends JpaRepository<PayrunConfig, UUID> {
    Optional<PayrunConfig> findByPayrunId(UUID payrunId);
}
