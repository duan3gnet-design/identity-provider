package com.identity.provider.controller;

import com.identity.provider.entity.Role;
import com.identity.provider.entity.User;
import com.identity.provider.repository.RoleRepository;
import com.identity.provider.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

/**
 * Xử lý sau khi user đăng nhập thành công qua Google SSO.
 * Spring Security đã xác thực xong, ta chỉ cần đảm bảo
 * user tồn tại trong DB (auto-provision nếu chưa có).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SsoCallbackController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/oauth2/sso-callback")
    public String handleSsoCallback(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return "redirect:/login?error";
        }

        String email = extractEmail(oauth2User);
        String name  = extractName(oauth2User);

        if (email == null) {
            log.warn("SSO user has no email claim");
            return "redirect:/login?error";
        }

        // Auto-provision: tạo user nếu chưa tồn tại
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

            String username = email.split("@")[0] + "_" + System.currentTimeMillis();

            User newUser = User.builder()
                .username(username)
                .email(email)
                .fullName(name)
                .emailVerified(true)
                .build();
            newUser.getRoles().add(userRole);
            userRepository.save(newUser);
            log.info("Auto-provisioned SSO user: {}", email);
        }

        return "redirect:/";
    }

    private String extractEmail(OAuth2User user) {
        if (user instanceof OidcUser oidcUser) {
            return oidcUser.getEmail();
        }
        Object email = user.getAttribute("email");
        return email != null ? email.toString() : null;
    }

    private String extractName(OAuth2User user) {
        if (user instanceof OidcUser oidcUser && oidcUser.getFullName() != null) {
            return oidcUser.getFullName();
        }
        Object name = user.getAttribute("name");
        return name != null ? name.toString() : null;
    }
}
