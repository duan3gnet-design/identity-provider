package com.identity.provider.repository;

import com.identity.provider.entity.MfaBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, UUID> {
    List<MfaBackupCode> findByUserIdAndUsedFalse(UUID userId);

    @Modifying
    @Query("UPDATE MfaBackupCode b SET b.used = true WHERE b.id = :id")
    void markUsed(UUID id);

    @Modifying
    @Query("DELETE FROM MfaBackupCode b WHERE b.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
