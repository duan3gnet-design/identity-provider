package com.identity.provider.repository;

import com.identity.provider.entity.OAuth2Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuth2ClientRepository extends JpaRepository<OAuth2Client, UUID> {
    Optional<OAuth2Client> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
