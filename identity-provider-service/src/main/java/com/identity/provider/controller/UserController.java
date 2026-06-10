package com.identity.provider.controller;

import com.identity.provider.dto.ApiResponse;
import com.identity.provider.dto.RegisterRequest;
import com.identity.provider.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Public: đăng ký tài khoản mới */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest req) {
        var user = userService.register(req);
        return ResponseEntity.ok(ApiResponse.ok("User registered successfully", user.getId()));
    }

    /** Admin: list all users */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userService.findAll()));
    }

    /** Admin: assign role */
    @PostMapping("/admin/users/{userId}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignRole(
        @PathVariable UUID userId,
        @PathVariable String role
    ) {
        userService.assignRole(userId, role);
        return ResponseEntity.ok(ApiResponse.ok("Role assigned", null));
    }

    /** Admin: disable/enable user */
    @PatchMapping("/admin/users/{userId}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setEnabled(
        @PathVariable UUID userId,
        @RequestParam boolean enabled
    ) {
        userService.setEnabled(userId, enabled);
        return ResponseEntity.ok(ApiResponse.ok(enabled ? "User enabled" : "User disabled", null));
    }
}
