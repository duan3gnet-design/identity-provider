package com.identity.provider.controller;

import com.identity.provider.entity.User;
import com.identity.provider.dto.ApiResponse;
import com.identity.provider.repository.UserRepository;
import com.identity.provider.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;

    /** Khởi tạo MFA — trả về secret + QR code base64 */
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<Map<String, String>>> setup(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        String secret = mfaService.initializeMfa(user.getId());
        String qrCode = mfaService.generateQrCodeBase64(user.getUsername(), secret);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "secret", secret,
            "qrCode", "data:image/png;base64," + qrCode
        )));
    }

    /** Confirm và enable MFA sau khi user scan QR */
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<List<String>>> enable(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam String code
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean success = mfaService.enableMfa(user.getId(), code);
        if (!success) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid OTP code"));
        }

        List<String> backupCodes = mfaService.generateBackupCodes(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("MFA enabled. Save these backup codes!", backupCodes));
    }

    /** Disable MFA */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam String code
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!mfaService.verifyCode(user.getId(), code)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid OTP code"));
        }
        mfaService.disableMfa(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("MFA disabled", null));
    }

    /** Tạo lại backup codes */
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<ApiResponse<List<String>>> regenerateBackupCodes(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam String code
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!mfaService.verifyCode(user.getId(), code)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid OTP code"));
        }
        List<String> codes = mfaService.generateBackupCodes(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("New backup codes generated", codes));
    }
}
