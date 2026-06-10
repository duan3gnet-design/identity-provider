package com.identity.provider.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "oauth2_clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret; // null for PUBLIC clients

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Builder.Default
    @Column(name = "client_type")
    private String clientType = "CONFIDENTIAL"; // PUBLIC | CONFIDENTIAL

    @Builder.Default
    @Column(name = "grant_types", columnDefinition = "text[]")
    private List<String> grantTypes = new ArrayList<>();

    @Builder.Default
    @Column(name = "redirect_uris", columnDefinition = "text[]")
    private List<String> redirectUris = new ArrayList<>();

    @Builder.Default
    @Column(name = "scopes", columnDefinition = "text[]")
    private List<String> scopes = new ArrayList<>();

    @Builder.Default
    @Column(name = "access_token_ttl_seconds")
    private int accessTokenTtlSeconds = 3600;

    @Builder.Default
    @Column(name = "refresh_token_ttl_seconds")
    private int refreshTokenTtlSeconds = 86400;

    @Builder.Default
    @Column(name = "id_token_ttl_seconds")
    private int idTokenTtlSeconds = 3600;

    @Builder.Default
    @Column(name = "require_pkce")
    private boolean requirePkce = true;

    @Builder.Default
    @Column(name = "allow_offline_access")
    private boolean allowOfflineAccess = false;

    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
