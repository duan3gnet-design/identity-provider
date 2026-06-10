package com.identity.provider.security;

import lombok.Getter;
import org.springframework.security.authentication.AccountStatusException;

/**
 * Thrown khi user cần xác thực MFA sau khi đăng nhập thành công bằng password.
 */
@Getter
public class MfaRequiredException extends AccountStatusException {

    private final String username;

    public MfaRequiredException(String username) {
        super("MFA verification required for: " + username);
        this.username = username;
    }

}
