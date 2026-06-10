package com.identity.provider.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.identity.provider.config.IdpProperties;
import com.identity.provider.entity.MfaBackupCode;
import com.identity.provider.entity.MfaSecret;
import com.identity.provider.repository.MfaBackupCodeRepository;
import com.identity.provider.repository.MfaSecretRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MfaService {

    private final MfaSecretRepository mfaSecretRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdpProperties idpProperties;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
        new DefaultCodeGenerator(), new SystemTimeProvider()
    );

    /**
     * Khởi tạo TOTP secret cho user (chưa enable, chờ verify).
     * @return secret (dạng base32)
     */
    @Transactional
    public String initializeMfa(UUID userId) {
        String newSecret = secretGenerator.generate();

        // Tìm bản ghi cũ, nếu có thì cập nhật, nếu không thì tạo mới
        MfaSecret mfaSecret = mfaSecretRepository.findByUserId(userId)
                .map(existingSecret -> {
                    existingSecret.setSecret(newSecret);
                    existingSecret.setEnabled(false);
                    return existingSecret;
                })
                .orElseGet(() -> MfaSecret.builder()
                        .userId(userId)
                        .secret(newSecret)
                        .enabled(false)
                        .build());

        mfaSecretRepository.save(mfaSecret);
        return newSecret;
    }

    /**
     * Tạo QR code PNG dưới dạng base64 để hiện lên UI.
     */
    public String generateQrCodeBase64(String username, String secret) {
        try {
            String issuer = idpProperties.getIssuer();
            String otpAuthUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                URLEncoder.encode(issuer, StandardCharsets.UTF_8),
                URLEncoder.encode(username, StandardCharsets.UTF_8),
                secret,
                URLEncoder.encode(issuer, StandardCharsets.UTF_8)
            );

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Verify OTP code và enable MFA nếu đúng.
     */
    @Transactional
    public boolean enableMfa(UUID userId, String otpCode) {
        MfaSecret mfaSecret = mfaSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("MFA not initialized for user"));

        if (!codeVerifier.isValidCode(mfaSecret.getSecret(), otpCode)) {
            return false;
        }

        mfaSecret.setEnabled(true);
        mfaSecretRepository.save(mfaSecret);

        // Generate backup codes
        generateBackupCodes(userId);
        return true;
    }

    /**
     * Verify OTP code lúc login.
     */
    public boolean verifyCode(UUID userId, String otpCode) {
        return mfaSecretRepository.findByUserId(userId)
            .filter(MfaSecret::isEnabled)
            .map(secret -> codeVerifier.isValidCode(secret.getSecret(), otpCode))
            .orElse(false);
    }

    /**
     * Verify backup code (một lần dùng).
     */
    @Transactional
    public boolean verifyBackupCode(UUID userId, String rawCode) {
        List<MfaBackupCode> codes = mfaBackupCodeRepository.findByUserIdAndUsedFalse(userId);
        for (MfaBackupCode backup : codes) {
            if (passwordEncoder.matches(rawCode.replace("-", ""), backup.getCodeHash())) {
                mfaBackupCodeRepository.markUsed(backup.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra user có MFA enabled không.
     */
    public boolean isMfaEnabled(UUID userId) {
        return mfaSecretRepository.findByUserId(userId)
            .map(MfaSecret::isEnabled)
            .orElse(false);
    }

    @Transactional
    public void disableMfa(UUID userId) {
        mfaSecretRepository.findByUserId(userId).ifPresent(s -> {
            s.setEnabled(false);
            mfaSecretRepository.save(s);
        });
        mfaBackupCodeRepository.deleteAllByUserId(userId);
    }

    /**
     * Generate 8 backup codes, hash và lưu vào DB.
     * @return list raw codes (chỉ hiện 1 lần)
     */
    @Transactional
    public List<String> generateBackupCodes(UUID userId) {
        mfaBackupCodeRepository.deleteAllByUserId(userId);

        SecureRandom random = new SecureRandom();
        List<String> rawCodes = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            String code = String.format("%04d-%04d", random.nextInt(10000), random.nextInt(10000));
            rawCodes.add(code);

            MfaBackupCode backup = MfaBackupCode.builder()
                .userId(userId)
                .codeHash(passwordEncoder.encode(code.replace("-", "")))
                .build();
            mfaBackupCodeRepository.save(backup);
        }

        return rawCodes;
    }
}
