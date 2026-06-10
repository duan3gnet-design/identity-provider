package com.identity.provider.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "signing_keys")
public class SigningKey {

    @Id
    private UUID id;

    private String keyId;       // kid in JWK

    @Builder.Default
    private String algorithm = "RS256";

    private String publicKeyPem;
    private String privateKeyPem;

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;
    private Instant expiresAt;
}
