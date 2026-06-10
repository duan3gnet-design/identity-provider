package com.identity.provider.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private UUID id;

    private String tokenHash;
    private UUID userId;
    private String clientId;
    private List<String> scopes;

    @Builder.Default
    private boolean revoked = false;

    private Instant expiresAt;
    private Instant createdAt;
}
