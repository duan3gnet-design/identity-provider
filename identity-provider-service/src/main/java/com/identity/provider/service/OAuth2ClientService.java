package com.identity.provider.service;

import com.identity.provider.entity.OAuth2Client;
import com.identity.provider.dto.CreateClientRequest;
import com.identity.provider.repository.OAuth2ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2ClientService {

    private final OAuth2ClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    public Optional<OAuth2Client> findById(String id) {
        try {
            return repository.findById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<OAuth2Client> findByClientId(String clientId) {
        return repository.findByClientId(clientId);
    }

    public List<OAuth2Client> findAll() {
        return repository.findAll();
    }

    @Transactional
    public OAuth2Client create(CreateClientRequest req) {
        if (repository.existsByClientId(req.getClientId())) {
            throw new IllegalArgumentException("Client ID already exists: " + req.getClientId());
        }

        String encodedSecret = null;
        if ("CONFIDENTIAL".equalsIgnoreCase(req.getClientType()) && req.getClientSecret() != null) {
            encodedSecret = passwordEncoder.encode(req.getClientSecret());
        }

        OAuth2Client client = OAuth2Client.builder()
            .clientId(req.getClientId())
            .clientSecret(encodedSecret)
            .clientName(req.getClientName())
            .clientType(req.getClientType())
            .grantTypes(req.getGrantTypes())
            .redirectUris(req.getRedirectUris())
            .scopes(req.getScopes())
            .accessTokenTtlSeconds(req.getAccessTokenTtlSeconds())
            .refreshTokenTtlSeconds(req.getRefreshTokenTtlSeconds())
            .requirePkce(req.isRequirePkce())
            .allowOfflineAccess(req.isAllowOfflineAccess())
            .build();

        return repository.save(client);
    }

    @Transactional
    public void delete(String clientId) {
        repository.findByClientId(clientId).ifPresent(repository::delete);
    }
}
