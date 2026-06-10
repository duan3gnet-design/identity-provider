package com.identity.provider.controller;

import com.identity.provider.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserRepository userRepository;

    @GetMapping("/")
    @PreAuthorize("isAuthenticated()")
    public String home(@AuthenticationPrincipal(errorOnInvalidType = true) UserDetails userDetails, Model model) {
        if (userDetails != null) {
            userRepository.findByUsername(userDetails.getUsername()).ifPresent(user -> {
                model.addAttribute("user", user);
                model.addAttribute("mfaEnabled",
                    user.getRoles() != null); // placeholder, sẽ dùng MfaService
            });
        }
        return "home";
    }
}
