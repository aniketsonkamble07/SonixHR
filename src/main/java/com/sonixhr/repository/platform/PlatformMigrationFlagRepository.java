package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformMigrationFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformMigrationFlagRepository extends JpaRepository<PlatformMigrationFlag, String> {
}
