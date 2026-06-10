package com.identity.provider.service;

import com.identity.provider.entity.Role;
import com.identity.provider.entity.User;
import com.identity.provider.dto.RegisterRequest;
import com.identity.provider.exception.UserAlreadyExistsException;
import com.identity.provider.repository.RoleRepository;
import com.identity.provider.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new UserAlreadyExistsException("Username already taken: " + req.getUsername());
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + req.getEmail());
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
            .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER not found"));

        User user = User.builder()
            .username(req.getUsername())
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .fullName(req.getFullName())
            .build();

        user.getRoles().add(userRole);
        return userRepository.save(user);
    }

    @Transactional
    public void assignRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        user.getRoles().add(role);
        userRepository.save(user);
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setEnabled(enabled);
            userRepository.save(user);
        });
    }
}
