package com.identity.provider.config;

import com.identity.provider.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customize nội dung JWT token trước khi SAS ký và trả về.
 * Thêm: roles, email, user_id vào cả access_token và id_token.
 */
@Configuration
@RequiredArgsConstructor
public class TokenCustomizerConfig {

    private final UserRepository userRepository;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            String principalName = context.getPrincipal().getName();

            userRepository.findByUsernameOrEmail(principalName).ifPresent(user -> {
                Set<String> roles = user.getRoles().stream()
                    .map(role -> role.getName())
                    .collect(Collectors.toSet());

                // Thêm vào access_token
                if (context.getTokenType().getValue().equals("access_token")) {
                    context.getClaims()
                        .claim("roles", roles)
                        .claim("email", user.getEmail())
                        .claim("user_id", user.getId().toString())
                        .claim("email_verified", user.isEmailVerified());
                }

                // Thêm vào id_token (OIDC)
                if (context.getTokenType().getValue().equals("id_token")) {
                    context.getClaims()
                        .claim("email", user.getEmail())
                        .claim("email_verified", user.isEmailVerified())
                        .claim("name", user.getFullName())
                        .claim("picture", user.getAvatarUrl())
                        .claim("roles", roles);
                }
            });
        };
    }
}
