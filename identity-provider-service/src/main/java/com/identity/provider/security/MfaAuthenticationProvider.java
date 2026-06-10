package com.identity.provider.security;

import com.identity.provider.entity.User;
import com.identity.provider.repository.UserRepository;
import com.identity.provider.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Custom AuthenticationProvider để intercept login và check MFA.
 * <p>
 * Flow:
 *  1. Verify username + password
 *  2. Nếu MFA enabled → throw MfaRequiredException (redirect sang /mfa/verify)
 *  3. Nếu không có MFA → authenticate bình thường
 */
@Component
@RequiredArgsConstructor
public class MfaAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        User user = userRepository.findByUsernameOrEmail(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        if (user.isLocked()) {
            throw new LockedException("Account is locked");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // Nếu MFA enabled → throw để trigger MFA flow
        if (mfaService.isMfaEnabled(user.getId())) {
            throw new MfaRequiredException(user.getUsername());
        }

        var authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName()))
            .collect(Collectors.toSet());

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .accountLocked(user.isLocked())
                .build();

        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
