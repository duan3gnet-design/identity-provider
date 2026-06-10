package com.identity.provider.controller;

import com.identity.provider.dto.ApiResponse;
import com.identity.provider.dto.CreateClientRequest;
import com.identity.provider.service.OAuth2ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/clients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ClientController {

    private final OAuth2ClientService clientService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> list() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CreateClientRequest req) {
        var client = clientService.create(req);
        return ResponseEntity.ok(ApiResponse.ok("Client created", client.getClientId()));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String clientId) {
        clientService.delete(clientId);
        return ResponseEntity.ok(ApiResponse.ok("Client deleted", null));
    }
}
