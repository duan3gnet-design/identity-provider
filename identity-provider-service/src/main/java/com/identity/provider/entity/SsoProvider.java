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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sso_providers")
public class SsoProvider {

    @Id
    private UUID id;

    private String providerName;
    private String clientId;
    private String clientSecret;
    private String authorizationUri;
    private String tokenUri;
    private String userinfoUri;
    private String jwkSetUri;
    private List<String> scopes;

    @Builder.Default
    private boolean enabled = true;

    private Instant createdAt;
}
