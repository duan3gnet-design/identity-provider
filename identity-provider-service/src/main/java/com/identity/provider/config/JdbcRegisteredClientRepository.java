package com.identity.provider.config;

import com.identity.provider.service.OAuth2ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * SAS gọi interface này để load OAuth2 client.
 * Mình implement bằng cách delegate xuống DB thông qua OAuth2ClientService.
 */
@Component
@RequiredArgsConstructor
public class JdbcRegisteredClientRepository implements RegisteredClientRepository {

    private final OAuth2ClientService clientService;

    @Override
    public void save(RegisteredClient registeredClient) {
        // Dùng API riêng để tạo client — không cần implement ở đây
        throw new UnsupportedOperationException("Use OAuth2ClientService to manage clients");
    }

    @Override
    public RegisteredClient findById(String id) {
        return clientService.findById(id)
            .map(this::toRegisteredClient)
            .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clientService.findByClientId(clientId)
            .map(this::toRegisteredClient)
            .orElse(null);
    }

    private RegisteredClient toRegisteredClient(com.identity.provider.entity.OAuth2Client client) {
        RegisteredClient.Builder builder = RegisteredClient
            .withId(client.getId().toString())
            .clientId(client.getClientId())
            .clientName(client.getClientName());

        // Client secret (null cho PUBLIC clients)
        if (client.getClientSecret() != null) {
            builder.clientSecret(client.getClientSecret());
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }

        // Grant types
        for (String grantType : client.getGrantTypes()) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }

        // Redirect URIs
        client.getRedirectUris().forEach(builder::redirectUri);

        // Scopes
        for (String scope : client.getScopes()) {
            builder.scope(scope);
        }

        // Token settings
        TokenSettings tokenSettings = TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtlSeconds()))
            .refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtlSeconds()))
            .reuseRefreshTokens(false) // Rotate refresh tokens
            .build();

        // Client settings
        ClientSettings clientSettings = ClientSettings.builder()
            .requireProofKey(client.isRequirePkce())           // PKCE
            .requireAuthorizationConsent(true)                 // Consent screen
            .build();

        return builder
            .tokenSettings(tokenSettings)
            .clientSettings(clientSettings)
            .build();
    }
}
