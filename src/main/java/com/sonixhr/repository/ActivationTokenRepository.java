package com.sonixhr.repository;

import com.sonixhr.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivationTokenRepository
        extends JpaRepository<ActivationToken, UUID> {

    Optional<ActivationToken> findByToken(String token);
}
