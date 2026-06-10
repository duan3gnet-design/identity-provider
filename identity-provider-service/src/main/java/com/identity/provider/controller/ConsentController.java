package com.identity.provider.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.*;

/**
 * Render consent page khi SAS redirect tới /oauth2/consent.
 *
 * Form submit: POST /oauth2/authorize với:
 *   - consent_action = "approve" | "deny"
 *   - scope          = các scope user đồng ý (multi-value)
 *   - state          = state từ SAS (bắt buộc)
 *   - client_id      = client đang request
 */
@Controller
@RequiredArgsConstructor
public class ConsentController {

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationConsentService authorizationConsentService;

    @GetMapping("/oauth2/consent")
    public String consent(
        Principal principal,
        Model model,
        @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
        @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
        @RequestParam(OAuth2ParameterNames.STATE) String state
    ) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) return "redirect:/error";

        // Scope user đã approve trước đó (lưu trong DB)
        Set<String> previouslyApproved = new HashSet<>();
        OAuth2AuthorizationConsent existingConsent =
            authorizationConsentService.findById(client.getId(), principal.getName());
        if (existingConsent != null) {
            existingConsent.getScopes().forEach(previouslyApproved::add);
        }

        // Tất cả scope đang được request
        Set<String> requestedScopes = new LinkedHashSet<>(Arrays.asList(scope.split(" ")));

        // Scope chưa approve → cần user confirm
        Set<String> scopesToApprove = new LinkedHashSet<>(requestedScopes);
        scopesToApprove.removeAll(previouslyApproved);

        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("state", state);
        model.addAttribute("principalName", principal.getName());
        model.addAttribute("scopes", withDescriptions(scopesToApprove));
        model.addAttribute("approvedScopes", withDescriptions(previouslyApproved));

        return "consent";
    }

    private List<Map<String, String>> withDescriptions(Set<String> scopes) {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put(OidcScopes.OPENID,  "Xác thực danh tính");
        desc.put(OidcScopes.PROFILE, "Truy cập tên và ảnh đại diện");
        desc.put(OidcScopes.EMAIL,   "Truy cập địa chỉ email");
        desc.put("roles",            "Truy cập danh sách quyền (roles)");
        desc.put("offline_access",   "Duy trì đăng nhập (refresh token)");

        return scopes.stream()
            .map(s -> Map.of("name", s, "description", desc.getOrDefault(s, s)))
            .toList();
    }
}
