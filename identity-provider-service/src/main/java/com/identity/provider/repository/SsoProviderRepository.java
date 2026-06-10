package com.identity.provider.repository;

import com.identity.provider.entity.SsoProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SsoProviderRepository extends JpaRepository<SsoProvider, UUID> {
    SsoProvider findByProviderName(String providerName);
}
