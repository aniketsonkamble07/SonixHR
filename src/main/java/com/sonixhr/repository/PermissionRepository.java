package com.sonixhr.repository;

import com.sonixhr.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);
    Set<Permission> findByNameIn(Set<String> names);
    boolean existsByName(String name);


}