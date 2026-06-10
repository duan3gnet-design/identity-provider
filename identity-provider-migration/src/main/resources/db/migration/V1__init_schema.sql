-- V1: Base schema for Identity Provider

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(500),
    full_name       VARCHAR(255),
    avatar_url      VARCHAR(1000),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    locked          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- Roles
CREATE TABLE IF NOT EXISTS roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User roles mapping
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- OAuth2 Clients
CREATE TABLE IF NOT EXISTS oauth2_clients (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                   VARCHAR(255) NOT NULL UNIQUE,
    client_secret               VARCHAR(500),
    client_name                 VARCHAR(255) NOT NULL,
    client_type                 VARCHAR(50) NOT NULL DEFAULT 'CONFIDENTIAL', -- PUBLIC | CONFIDENTIAL
    grant_types                 TEXT[] NOT NULL DEFAULT '{authorization_code}',
    redirect_uris               TEXT[] NOT NULL DEFAULT '{}',
    scopes                      TEXT[] NOT NULL DEFAULT '{openid,profile,email}',
    access_token_ttl_seconds    INT NOT NULL DEFAULT 3600,
    refresh_token_ttl_seconds   INT NOT NULL DEFAULT 86400,
    id_token_ttl_seconds        INT NOT NULL DEFAULT 3600,
    require_pkce                BOOLEAN NOT NULL DEFAULT TRUE,
    allow_offline_access        BOOLEAN NOT NULL DEFAULT FALSE,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth2_clients_client_id ON oauth2_clients(client_id);

-- RSA Key Pairs (for JWT signing, rotatable)
CREATE TABLE IF NOT EXISTS signing_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id          VARCHAR(100) NOT NULL UNIQUE,
    algorithm       VARCHAR(20) NOT NULL DEFAULT 'RS256',
    public_key_pem  TEXT NOT NULL,
    private_key_pem TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ
);

-- Refresh Tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash      VARCHAR(500) NOT NULL UNIQUE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id       VARCHAR(255) NOT NULL,
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- MFA secrets (TOTP)
CREATE TABLE IF NOT EXISTS mfa_secrets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret      VARCHAR(500) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- MFA backup codes
CREATE TABLE IF NOT EXISTS mfa_backup_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(500) NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mfa_backup_codes_user_id ON mfa_backup_codes(user_id);

-- SSO Providers (external IdP: Google, GitHub, etc.)
CREATE TABLE IF NOT EXISTS sso_providers (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name     VARCHAR(100) NOT NULL UNIQUE,
    client_id         VARCHAR(500) NOT NULL,
    client_secret     VARCHAR(500) NOT NULL,
    authorization_uri VARCHAR(1000) NOT NULL,
    token_uri         VARCHAR(1000) NOT NULL,
    userinfo_uri      VARCHAR(1000) NOT NULL,
    jwk_set_uri       VARCHAR(1000),
    scopes            TEXT[] NOT NULL DEFAULT '{openid,profile,email}',
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SSO linked accounts
CREATE TABLE IF NOT EXISTS sso_linked_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_name    VARCHAR(100) NOT NULL,
    provider_user_id VARCHAR(500) NOT NULL,
    access_token     TEXT,
    refresh_token    TEXT,
    linked_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider_name, provider_user_id)
);

-- Seed default roles
INSERT INTO roles (name, description) VALUES
    ('ROLE_USER', 'Standard user'),
    ('ROLE_ADMIN', 'Administrator')
ON CONFLICT (name) DO NOTHING;

-- Seed a default public client
INSERT INTO oauth2_clients (client_id, client_name, client_type, grant_types, redirect_uris, scopes, require_pkce, allow_offline_access)
VALUES (
    'idp-web-client',
    'IDP Web Client',
    'PUBLIC',
    '{authorization_code,refresh_token}',
    '{http://localhost:3000/callback,http://localhost:8080/swagger-ui/oauth2-redirect.html}',
    '{openid,profile,email,roles}',
    FALSE,
    TRUE
) ON CONFLICT (client_id) DO NOTHING;

-- Seed a confidential client (for backend-to-backend)
INSERT INTO oauth2_clients (client_id, client_secret, client_name, client_type, grant_types, redirect_uris, scopes, require_pkce, allow_offline_access)
VALUES (
    'idp-backend-client',
    '$2a$12$placeholder_hashed_secret',
    'IDP Backend Client',
    'CONFIDENTIAL',
    '{authorization_code,client_credentials,refresh_token}',
    '{http://localhost:8090/callback}',
    '{openid,profile,email,roles}',
    FALSE,
    FALSE
) ON CONFLICT (client_id) DO NOTHING;

-- =====================
-- OAUTH2 AUTHORIZATION
-- Lưu authorization code, access token, refresh token state
-- =====================
CREATE TABLE IF NOT EXISTS oauth2_authorization (
                                                    id                            VARCHAR(100) NOT NULL PRIMARY KEY,
                                                    registered_client_id          VARCHAR(100) NOT NULL,
                                                    principal_name                VARCHAR(200) NOT NULL,
                                                    authorization_grant_type      VARCHAR(100) NOT NULL,
                                                    authorized_scopes             VARCHAR(1000),
                                                    attributes                    TEXT,
                                                    state                         VARCHAR(500),
                                                    authorization_code_value      TEXT,
                                                    authorization_code_issued_at  TIMESTAMPTZ,
                                                    authorization_code_expires_at TIMESTAMPTZ,
                                                    authorization_code_metadata   TEXT,
                                                    access_token_value            TEXT,
                                                    access_token_issued_at        TIMESTAMPTZ,
                                                    access_token_expires_at       TIMESTAMPTZ,
                                                    access_token_metadata         TEXT,
                                                    access_token_type             VARCHAR(100),
                                                    access_token_scopes           VARCHAR(1000),
                                                    oidc_id_token_value           TEXT,
                                                    oidc_id_token_issued_at       TIMESTAMPTZ,
                                                    oidc_id_token_expires_at      TIMESTAMPTZ,
                                                    oidc_id_token_metadata        TEXT,
                                                    refresh_token_value           TEXT,
                                                    refresh_token_issued_at       TIMESTAMPTZ,
                                                    refresh_token_expires_at      TIMESTAMPTZ,
                                                    refresh_token_metadata        TEXT,
                                                    user_code_value               TEXT,
                                                    user_code_issued_at           TIMESTAMPTZ,
                                                    user_code_expires_at          TIMESTAMPTZ,
                                                    user_code_metadata            TEXT,
                                                    device_code_value             TEXT,
                                                    device_code_issued_at         TIMESTAMPTZ,
                                                    device_code_expires_at        TIMESTAMPTZ,
                                                    device_code_metadata          TEXT
);

-- =====================
-- OAUTH2 AUTHORIZATION CONSENT
-- Lưu scope đã được user approve cho từng client
-- SAS check bảng này để quyết định có hiện consent page không
-- =====================
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
                                                            registered_client_id VARCHAR(100)  NOT NULL,
                                                            principal_name       VARCHAR(200)  NOT NULL,
                                                            authorities          VARCHAR(1000) NOT NULL,
                                                            PRIMARY KEY (registered_client_id, principal_name)
);
