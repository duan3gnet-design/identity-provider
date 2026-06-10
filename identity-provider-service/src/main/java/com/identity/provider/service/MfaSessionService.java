package com.identity.provider.service;

import com.identity.provider.config.IdpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Lưu trạng thái MFA pending vào Redis.
 * Khi user nhập đúng password nhưng cần MFA, ta lưu username vào Redis
 * với key = session token. Sau khi verify OTP thành công, ta dùng username này
 * để tạo Authentication object.
 */
@Service
@RequiredArgsConstructor
public class MfaSessionService {

    private static final String PREFIX = "mfa:pending:";

    private final RedisTemplate<String, String> redisTemplate;
    private final IdpProperties idpProperties;

    /**
     * Tạo MFA pending session, trả về token để client dùng làm state.
     */
    public String createPendingSession(String username) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
            PREFIX + token,
            username,
            Duration.ofSeconds(idpProperties.getMfaSessionTtl())
        );
        return token;
    }

    /**
     * Lấy username từ pending session.
     */
    public String getUsername(String token) {
        return redisTemplate.opsForValue().get(PREFIX + token);
    }

    /**
     * Xóa session sau khi verify thành công.
     */
    public void invalidate(String token) {
        redisTemplate.delete(PREFIX + token);
    }
}
