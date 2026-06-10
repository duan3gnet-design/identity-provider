package com.identity.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateClientRequest {
    @NotBlank
    private String clientId;

    private String clientSecret; // null for PUBLIC

    @NotBlank
    private String clientName;

    private String clientType = "CONFIDENTIAL";

    @NotEmpty
    private List<String> grantTypes;

    @NotEmpty
    private List<String> redirectUris;

    @NotEmpty
    private List<String> scopes;

    private int accessTokenTtlSeconds = 3600;
    private int refreshTokenTtlSeconds = 86400;
    private boolean requirePkce = true;
    private boolean allowOfflineAccess = false;
}
