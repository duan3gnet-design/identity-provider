package com.identity.provider.repository;

import com.identity.provider.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    RefreshToken findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken SET revoked = TRUE WHERE tokenHash = :tokenHash")
    Integer revokeByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken SET revoked = TRUE WHERE userId = :userId")
    Integer revokeAllByUserId(UUID userId);
}
