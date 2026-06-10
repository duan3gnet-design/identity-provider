package com.identity.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "idp")
public class IdpProperties {
    private String issuer = "http://localhost:8080";
    private long mfaSessionTtl = 300; // seconds
}
