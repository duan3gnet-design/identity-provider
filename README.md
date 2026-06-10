# Identity Provider

OAuth2/OIDC Identity Provider xây dựng trên **Spring Boot 4.0.5** và **Spring Authorization Server**, hỗ trợ TOTP-based MFA, Google SSO, và quản lý OAuth2 clients.

---

## Mục lục

- [Kiến trúc tổng quan](#kiến-trúc-tổng-quan)
- [Cấu trúc project](#cấu-trúc-project)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Yêu cầu môi trường](#yêu-cầu-môi-trường)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Cấu hình](#cấu-hình)
- [OAuth2 Endpoints](#oauth2-endpoints)
- [Authentication Flow](#authentication-flow)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Seed Data mặc định](#seed-data-mặc-định)
- [Test Authorization Code Flow](#test-authorization-code-flow)
- [Ghi chú Production](#ghi-chú-production)

---

## Kiến trúc tổng quan

```
┌─────────────────────────────────────────────────────────┐
│                   Identity Provider                      │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │        Spring Authorization Server (SAS)         │   │
│  │  /oauth2/authorize   /oauth2/token               │   │
│  │  /oauth2/jwks        /oauth2/introspect           │   │
│  │  /oauth2/revoke      /userinfo                   │   │
│  │  /.well-known/openid-configuration               │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Login +    │  │  MFA (TOTP)  │  │  Google SSO   │  │
│  │  Custom     │  │  Redis       │  │  (upstream)   │  │
│  │  AuthMgr    │  │  Sessions    │  │               │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
│                                                         │
│  ┌───────────────────┐  ┌──────────────────────────┐   │
│  │  PostgreSQL        │  │  Redis                   │   │
│  │  - users/roles     │  │  - MFA pending sessions  │   │
│  │  - oauth2_clients  │  │  TTL: 300s               │   │
│  │  - oauth2_authz    │  └──────────────────────────┘   │
│  │  - oauth2_consent  │                                 │
│  │  - mfa_secrets     │                                 │
│  └───────────────────┘                                  │
└─────────────────────────────────────────────────────────┘
```

---

## Cấu trúc project

```
identity-provider/
├── identity-provider-migration/          # Flyway migration (chạy độc lập)
│   └── src/main/resources/db/migration/
│       └── V1__init_schema.sql           # Schema đầy đủ + seed data
│
└── identity-provider-service/           # Spring Boot application
    └── src/main/java/.../provider/
        ├── config/
        │   ├── SecurityConfig.java            # 2 security filter chains
        │   ├── JdbcRegisteredClientRepository # Load OAuth2 client từ DB
        │   ├── AuthorizationServiceConfig.java# JdbcOAuth2AuthorizationService
        │   ├── TokenCustomizerConfig.java     # Thêm roles/email vào JWT
        │   ├── JwkConfig.java                 # RSA key pair cho JWT signing
        │   ├── IdpProperties.java             # Custom config (issuer, MFA TTL)
        │   ├── EncoderConfig.java             # BCryptPasswordEncoder
        │   └── RedisConfig.java               # RedisTemplate
        │
        ├── entity/                       # JPA entities
        │   ├── User.java
        │   ├── Role.java
        │   ├── OAuth2Client.java
        │   ├── MfaSecret.java
        │   └── MfaBackupCode.java
        │
        ├── repository/                   # Spring Data JPA
        ├── service/
        │   ├── UserService.java          # Register, assign roles
        │   ├── OAuth2ClientService.java  # CRUD clients
        │   ├── MfaService.java           # TOTP setup/verify, backup codes
        │   └── MfaSessionService.java    # Redis pending MFA sessions
        │
        ├── security/
        │   ├── IdpUserDetailsService.java     # UserDetailsService cho SAS
        │   ├── MfaAuthenticationProvider.java # Intercept login, trigger MFA
        │   └── MfaRequiredException.java
        │
        ├── controller/
        │   ├── LoginController.java      # Custom login + MFA flow
        │   ├── ConsentController.java    # OAuth2 consent page
        │   ├── MfaSetupController.java   # Bật/tắt MFA qua UI
        │   ├── SsoCallbackController.java# Auto-provision Google SSO users
        │   ├── UserController.java       # REST API: register, admin
        │   ├── ClientController.java     # REST API: quản lý OAuth2 clients
        │   └── HomeController.java
        │
        └── resources/
            ├── templates/                # Thymeleaf
            │   ├── login.html
            │   ├── mfa-verify.html
            │   ├── mfa-setup.html
            │   ├── mfa-backup-codes.html
            │   ├── consent.html          # OAuth2 consent page (tiếng Việt)
            │   ├── register.html
            │   └── home.html
            └── static/css/auth.css
```

---

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Framework | Spring Boot 4.0.5, Spring MVC |
| Authorization Server | Spring Authorization Server (SAS) |
| Security | Spring Security 6 |
| Database | PostgreSQL 15+ |
| ORM | Spring Data JPA / Hibernate |
| Migration | Flyway 10 |
| Cache / Session | Redis |
| Template Engine | Thymeleaf |
| JWT | Nimbus JOSE (built-in SAS) |
| MFA | dev.samstevens.totp |
| QR Code | ZXing |
| Java | Java 21 |
| Build | Maven |

---

## Yêu cầu môi trường

- **Java 21+**
- **Maven 3.9+**
- **PostgreSQL 15+**
- **Redis 7+**

---

## Cài đặt và chạy

### 1. Tạo database

```sql
CREATE DATABASE identity_provider;
```

### 2. Clone và build

```bash
git clone <repo-url>
cd identity-provider
mvn clean install -DskipTests
```

### 3. Chạy Flyway migration

```bash
cd identity-provider-migration
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/identity_provider \
  -Dflyway.user=postgres \
  -Dflyway.password=your_password
```

Hoặc để service tự chạy Flyway khi khởi động (đã cấu hình sẵn trong `application.yml`).

### 4. Khởi động Redis

```bash
# Docker
docker run -d -p 6379:6379 redis:7-alpine

# Hoặc local
redis-server
```

### 5. Chạy service

```bash
cd identity-provider-service
mvn spring-boot:run
```

Hoặc với biến môi trường:

```bash
DB_URL=jdbc:postgresql://localhost:5432/identity_provider \
DB_USERNAME=postgres \
DB_PASSWORD=your_password \
GOOGLE_CLIENT_ID=your_google_client_id \
GOOGLE_CLIENT_SECRET=your_google_client_secret \
mvn spring-boot:run
```

Server chạy tại: `http://localhost:8080`

---

## Cấu hình

### `application.yml` — các giá trị quan trọng

```yaml
idp:
  issuer: http://localhost:8080      # Thay bằng domain thật khi deploy
  mfa-session-ttl: 300               # TTL pending MFA session (giây)

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/identity_provider}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:12345}

  data:
    redis:
      host: localhost
      port: 6379

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:change-me}
            client-secret: ${GOOGLE_CLIENT_SECRET:change-me}
```

### Environment variables

| Biến | Mô tả | Mặc định |
|---|---|---|
| `DB_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/identity_provider` |
| `DB_USERNAME` | DB username | `postgres` |
| `DB_PASSWORD` | DB password | `12345` |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | `change-me` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | `change-me` |

---

## OAuth2 Endpoints

SAS tự động publish các endpoint sau tại issuer `http://localhost:8080`:

| Endpoint | Mô tả |
|---|---|
| `GET /.well-known/openid-configuration` | OIDC Discovery document |
| `GET /oauth2/jwks` | JWK Set (public keys để verify JWT) |
| `GET /oauth2/authorize` | Authorization endpoint |
| `POST /oauth2/token` | Token endpoint |
| `POST /oauth2/revoke` | Token revocation |
| `POST /oauth2/introspect` | Token introspection |
| `GET /userinfo` | UserInfo endpoint (OIDC) |
| `GET /oauth2/consent` | Consent page (custom) |

---

## Authentication Flow

### Authorization Code + PKCE (khuyên dùng cho SPA/mobile)

```
Client                    IDP                         User
  │                        │                            │
  │─── GET /oauth2/authorize?client_id=...&code_challenge=... ──▶│
  │                        │◀── Redirect /login ────────│
  │                        │◀── POST /login/process ─────│ (username/password)
  │                        │                            │
  │         [Nếu MFA bật]  │                            │
  │                        │◀── Redirect /mfa/verify ───│
  │                        │◀── POST /mfa/verify ────────│ (TOTP code)
  │                        │                            │
  │                        │──── Redirect /oauth2/consent ──▶│
  │                        │◀── POST /oauth2/authorize?consent_action=approve ──│
  │                        │                            │
  │◀── Redirect ?code=... ─│                            │
  │─── POST /oauth2/token (code + code_verifier) ──▶│   │
  │◀── access_token + id_token + refresh_token ─────│   │
```

### Google SSO Flow

```
User ──▶ GET /oauth2/authorization/google
      ──▶ Google consent ──▶ GET /login/oauth2/code/google
      ──▶ /oauth2/sso-callback (auto-provision nếu user chưa tồn tại)
      ──▶ Redirect /
```

---

## API Reference

### Public

| Method | Endpoint | Mô tả |
|---|---|---|
| `POST` | `/api/register` | Đăng ký tài khoản mới |

**Request body `/api/register`:**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "password123",
  "fullName": "John Doe"
}
```

### Admin (yêu cầu `ROLE_ADMIN`)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/admin/users` | Danh sách tất cả users |
| `POST` | `/api/admin/users/{userId}/roles/{role}` | Gán role cho user |
| `PATCH` | `/api/admin/users/{userId}/enabled?enabled=true` | Enable/disable user |
| `GET` | `/api/admin/clients` | Danh sách OAuth2 clients |
| `POST` | `/api/admin/clients` | Tạo OAuth2 client mới |
| `DELETE` | `/api/admin/clients/{clientId}` | Xóa OAuth2 client |

**Request body `POST /api/admin/clients`:**
```json
{
  "clientId": "my-app",
  "clientSecret": "secret123",
  "clientName": "My Application",
  "clientType": "CONFIDENTIAL",
  "grantTypes": ["authorization_code", "refresh_token"],
  "redirectUris": ["https://myapp.com/callback"],
  "scopes": ["openid", "profile", "email", "roles"],
  "accessTokenTtlSeconds": 3600,
  "refreshTokenTtlSeconds": 86400,
  "requirePkce": false,
  "allowOfflineAccess": true
}
```

### MFA (yêu cầu đăng nhập)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/mfa/setup` | Trang cài đặt MFA (UI) |
| `POST` | `/mfa/setup/enable` | Xác nhận bật MFA bằng OTP |
| `POST` | `/mfa/disable` | Tắt MFA |
| `POST` | `/api/mfa/setup` | API: khởi tạo MFA (trả về secret + QR) |
| `POST` | `/api/mfa/enable?code=` | API: enable MFA |
| `POST` | `/api/mfa/disable?code=` | API: disable MFA |
| `POST` | `/api/mfa/backup-codes/regenerate?code=` | API: tạo lại backup codes |

---

## Database Schema

### Bảng chính

```
users                    oauth2_clients
├── id (UUID PK)         ├── id (UUID PK)
├── username (unique)    ├── client_id (unique)
├── email (unique)       ├── client_secret (nullable)
├── password_hash        ├── client_name
├── full_name            ├── client_type (PUBLIC/CONFIDENTIAL)
├── enabled              ├── grant_types (text[])
├── email_verified       ├── redirect_uris (text[])
└── locked               ├── scopes (text[])
                         ├── access_token_ttl_seconds
roles                    ├── refresh_token_ttl_seconds
├── id (UUID PK)         ├── require_pkce
└── name (unique)        └── allow_offline_access

user_roles               mfa_secrets
├── user_id (FK)         ├── user_id (FK, unique)
└── role_id (FK)         ├── secret (base32)
                         └── enabled

oauth2_authorization          oauth2_authorization_consent
└── (managed by SAS)          └── (managed by SAS)

mfa_backup_codes         sso_linked_accounts
├── user_id (FK)         ├── user_id (FK)
├── code_hash (BCrypt)   ├── provider_name
└── used                 └── provider_user_id
```

---

## Seed Data mặc định

Sau khi chạy migration, DB có sẵn:

**Roles:**
- `ROLE_USER` — quyền cơ bản
- `ROLE_ADMIN` — quản trị viên

**OAuth2 Clients:**

| Client ID | Type | Grant Types | PKCE | Mục đích |
|---|---|---|---|---|
| `idp-web-client` | PUBLIC | authorization_code, refresh_token | Bắt buộc | SPA, Postman test |
| `idp-backend-client` | CONFIDENTIAL | authorization_code, client_credentials, refresh_token | Không | Backend services |

> **Lưu ý:** `idp-backend-client` có `client_secret` là placeholder. Cần cập nhật bằng BCrypt hash thực tế trước khi dùng.

---

## Test Authorization Code Flow

### Với Postman / trình duyệt

1. Mở URL sau trong trình duyệt:

```
http://localhost:8080/oauth2/authorize
  ?response_type=code
  &client_id=idp-web-client
  &redirect_uri=http://localhost:3000/callback
  &scope=openid profile email roles
  &state=random_state_value
```

2. Đăng nhập → (MFA nếu bật) → Consent page → Approve

3. Browser redirect về `http://localhost:3000/callback?code=...&state=...`

4. Exchange code lấy token:

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=<authorization_code>" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=idp-web-client"
```

5. Response:

```json
{
  "access_token": "eyJraWQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "id_token": "eyJraWQ...",
  "scope": "openid profile email roles"
}
```

### Decode JWT

Access token chứa các claims tùy chỉnh:

```json
{
  "sub": "johndoe",
  "iss": "http://localhost:8080",
  "roles": ["ROLE_USER"],
  "email": "john@example.com",
  "user_id": "550e8400-...",
  "email_verified": false,
  "exp": 1234567890
}
```

### Xóa consent để test lại consent page

```sql
DELETE FROM oauth2_authorization_consent;
```

---

## Ghi chú Production

### RSA Key persistence

Hiện tại RSA key được **generate in-memory** khi khởi động (`JwkConfig.java`). Khi restart, key mới được tạo ra, khiến tất cả JWT cũ bị invalidate.

Để production, thay bằng key lưu trong DB hoặc Vault:

```java
// TODO: Load key từ bảng signing_keys thay vì generate mới
```

### HTTPS

SAS phải chạy sau HTTPS trong production. Cập nhật `idp.issuer`:

```yaml
idp:
  issuer: https://auth.yourdomain.com
```

### Google OAuth2

Đăng ký OAuth2 credentials tại [Google Cloud Console](https://console.cloud.google.com/) và thêm authorized redirect URI:

```
https://your-idp-domain.com/login/oauth2/code/google
```

### Redis

Nên bật Redis authentication và TLS trong production:

```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}
      ssl:
        enabled: true
```
