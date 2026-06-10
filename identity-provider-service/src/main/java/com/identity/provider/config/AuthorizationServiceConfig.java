package com.identity.provider.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Dùng JDBC thay vì InMemory cho OAuth2AuthorizationService
 * và OAuth2AuthorizationConsentService.
 *
 * Tại sao quan trọng:
 * - OAuth2AuthorizationService: persist authorization code, tokens vào DB
 *   thay vì mất khi restart.
 * - OAuth2AuthorizationConsentService: SAS dùng service này để check xem
 *   user đã approve scope cho client chưa. Nếu dùng InMemory thì mỗi
 *   restart lại mất → user phải approve lại. Nhưng nguy hiểm hơn là
 *   nếu consent record tồn tại trong memory từ request trước, SAS sẽ
 *   SKIP consent page hoàn toàn → đây chính là bug đang gặp.
 *
 *   Với JdbcOAuth2AuthorizationConsentService:
 *   - Consent được lưu vào bảng oauth2_authorization_consent
 *   - User approve một lần → lưu vào DB → lần sau skip (expected behavior)
 *   - Để force hiện consent page mọi lần: xóa record trong DB,
 *     hoặc set requireAuthorizationConsent(true) + không lưu consent (xem below)
 */
@Configuration
public class AuthorizationServiceConfig {

    @Bean
    public OAuth2AuthorizationService authorizationService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }
}
