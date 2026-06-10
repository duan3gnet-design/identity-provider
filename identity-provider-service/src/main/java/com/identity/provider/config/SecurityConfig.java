package com.identity.provider.config;

import com.identity.provider.security.IdpUserDetailsService;
import com.identity.provider.security.MfaAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final IdpUserDetailsService userDetailsService;
    private final MfaAuthenticationProvider mfaAuthenticationProvider;
    private final IdpProperties idpProperties;

    /**
     * Chain 1 — Spring Authorization Server endpoints.
     * Bắt: /oauth2/authorize, /oauth2/token, /oauth2/jwks,
     *       /.well-known/openid-configuration, /userinfo, v.v.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authServerConfigurer =
            new OAuth2AuthorizationServerConfigurer();

        http
            .securityMatcher(authServerConfigurer.getEndpointsMatcher())
            .with(authServerConfigurer, configurer -> configurer
                .oidc(Customizer.withDefaults())
                .authorizationEndpoint(endpoint ->
                    endpoint.consentPage("/oauth2/consent")
                )
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            // Redirect về /login nếu chưa authenticate (browser request)
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            );

        return http.build();
    }

    /**
     * Chain 2 — Tất cả endpoint còn lại.
     *
     * QUAN TRỌNG: Không dùng .formLogin() với loginProcessingUrl vì ta tự
     * xử lý POST /login/process trong LoginController (cần intercept MFA).
     * Chỉ set loginPage để Spring Security biết redirect về đâu khi 401.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // Bỏ CSRF check cho API endpoint (dùng JSON)
                .ignoringRequestMatchers("/api/register", "/api/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/login/process",
                    "/mfa/verify",
                    "/register",
                    "/api/register",
                    "/error",
                    "/css/**", "/js/**", "/images/**",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // Chỉ khai báo loginPage — KHÔNG khai báo loginProcessingUrl
            // để tránh Spring Security's UsernamePasswordAuthenticationFilter
            // bắt POST /login/process trước LoginController
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login/process-disabled") // URL không dùng → vô hiệu hoá filter
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // Google SSO upstream
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/oauth2/sso-callback", true)
            );

        return http.build();
    }

    /**
     * AuthenticationManager — inject vào LoginController để tự xử lý login.
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider(userDetailsService);
        daoProvider.setPasswordEncoder(passwordEncoder);
        // MfaAuthenticationProvider được xét trước — throw MfaRequiredException nếu cần MFA
        return new ProviderManager(mfaAuthenticationProvider, daoProvider);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer(idpProperties.getIssuer())
            .build();
    }
}
