package com.identity.provider.repository;

import com.identity.provider.entity.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {
    SigningKey findFirstByActiveTrue();
    SigningKey findAllByActiveTrue();
    SigningKey findByKeyId(String keyId);
}
