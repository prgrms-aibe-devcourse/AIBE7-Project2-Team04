# Spring Security 및 JWT 기본 설정 TODO

## 1. 목표와 전제

- LOCAL 이메일·비밀번호 로그인과 카카오·Google OAuth2 로그인을 지원한다.
- Access Token은 HS256 JWT로 발급하고 API 요청의 `Authorization: Bearer {token}` 헤더로 전달한다.
- Refresh Token은 무작위 opaque token으로 발급하고 Secure/HttpOnly 쿠키로 전달하며, 서버 DB에는 해시만 저장한다.
- Access Token 유효기간은 15분, Refresh Token 유효기간은 14일로 설정한다.
- 초기 개발은 프론트엔드와 백엔드가 같은 Origin이라고 가정하여 별도 CORS 설정을 보류한다.
- CORS와 CSRF는 서로 다른 개념이므로 Refresh Token API 구현 시 CSRF 보호를 반드시 적용한다.

---

## 2. 구현 전 필수 확인

### 2.1 JWT 비밀키 준비

- [x] HS256용 32바이트 이상의 무작위 키를 생성한다.
- [x] 생성한 값을 Base64 문자열로 `.env`의 `JWT_SECRET`에 저장한다.
- [x] JWT 비밀키를 Git, 로그, API 응답에 노출하지 않는다.
- [x] 카카오·Google Client Secret이나 DB 비밀번호와 같은 값을 사용하지 않는다.

키 생성 예시:

```bash
openssl rand -base64 32
```

환경변수:

```dotenv
JWT_ISSUER=project2
JWT_SECRET=<Base64로 인코딩된 32바이트 이상의 무작위 키>
```

### 2.2 인증 의존성 활성화

- [ ] OAuth2 Client 의존성의 주석을 해제한다.
- [ ] OAuth2 Resource Server 의존성의 주석을 해제한다.
- [ ] Argon2 구현에 필요한 암호화 라이브러리를 추가한다.

```gradle
// 카카오·Google OAuth2 로그인
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client'

// JWT Bearer Token 검증
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
```

Spring Security `JwtEncoder`, `JwtDecoder`를 사용하므로 별도의 `jjwt` 의존성은 추가하지 않는다.

### 2.3 Argon2 구현 방식 확정

- [ ] 비밀번호는 Argon2로 단방향 해싱한다.
- [ ] 원문 비밀번호를 DB나 로그에 저장하지 않는다.
- [ ] 비밀번호 해시에 `{argon2}` 알고리즘 식별자를 포함한다.
- [ ] 서버 환경에서 비밀번호 검증 시간이 지나치게 길거나 짧지 않은지 측정한다.

저장 예시:

```text
{argon2}$argon2id$...
```

### 2.4 API 접근 정책 확정

인증 없이 허용할 경로:

```text
POST /auth/signup
POST /auth/login
POST /auth/token/refresh
GET  /oauth2/authorization/**
GET  /login/oauth2/code/**
POST /auth/oauth2/exchange
GET  /swagger-ui/**
GET  /v3/api-docs/**
GET  /actuator/health
```

인증이 필요한 경로:

```text
/api/**
/users/**
/matches/**
/chatrooms/**
/mypage/**
```

관리자 권한이 필요한 경로:

```text
/admin/**
```

---

## 3. 기본 보안 설정 구현 순서

### 3.1 인증 설정 프로퍼티

- [ ] `app.auth` 설정을 `@ConfigurationProperties`로 연결한다.
- [ ] `PasswordProperties`, `JwtProperties`를 정의한다.
- [ ] 설정 프로퍼티 스캔 또는 등록을 활성화한다.
- [ ] 애플리케이션 시작 시 설정값을 검증한다.

권장 구조:

```text
global/security/
├── AuthProperties.java
├── PasswordConfig.java
└── JwtConfig.java
```

검증 항목:

- `JWT_SECRET`이 존재하는가
- Base64로 디코딩 가능한가
- 디코딩 결과가 32바이트 이상인가
- Access Token 유효기간이 Refresh Token보다 짧은가
- issuer 값이 비어 있지 않은가

### 3.2 비밀번호 암호화 설정

- [ ] `PasswordEncoder` Bean을 등록한다.
- [ ] 기본 인코딩 알고리즘을 Argon2로 설정한다.
- [ ] 회원가입에는 `encode`, 로그인에는 `matches`를 사용한다.
- [ ] LOCAL 계정에만 비밀번호 인증을 허용한다.

```text
provider = LOCAL
→ password_hash 필수

provider = KAKAO 또는 GOOGLE
→ password_hash는 NULL
```

### 3.3 HMAC JWT 발급·검증 설정

- [ ] `JWT_SECRET`을 Base64로 디코딩하여 `SecretKey`를 생성한다.
- [ ] 동일한 `SecretKey`로 `JwtEncoder`와 `JwtDecoder`를 구성한다.
- [ ] 발급 및 검증 알고리즘을 HS256으로 제한한다.
- [ ] issuer, audience, 만료시간을 검증한다.

```text
JWT_SECRET
   ↓ Base64 decode
SecretKey
   ├── JwtEncoder: JWT 발급
   └── JwtDecoder: JWT 검증
```

필수 JWT 클레임:

```text
iss   발급자(project2)
sub   사용자 UUID
aud   project2-api
iat   발급 시각
exp   만료 시각
jti   토큰 고유 UUID
roles 사용자 권한
```

JWT에 포함하지 않을 정보:

- 비밀번호 또는 비밀번호 해시
- Refresh Token
- OAuth Provider ID
- 불필요한 개인정보

### 3.4 JWT 발급 서비스

- [ ] `JwtTokenService`를 구현한다.
- [ ] 사용자 UUID와 권한으로 Access Token을 생성한다.
- [ ] Access Token 만료시간을 응답에 함께 제공한다.
- [ ] 발급 시각과 만료 시각 계산을 서버 시간 기준으로 통일한다.

권장 책임:

```text
JwtTokenService
├── createAccessToken(User)
└── getAccessTokenExpiresIn()
```

### 3.5 사용자 인증 객체

- [ ] `CustomUserPrincipal`을 구현한다.
- [ ] `CustomUserDetailsService`를 구현한다.
- [ ] 이메일을 정규화한 뒤 사용자를 조회한다.
- [ ] 탈퇴 또는 비활성 상태 사용자의 인증을 차단한다.

인증 객체가 제공할 정보:

```text
사용자 UUID
이메일
passwordHash
UserRole
UserStatus
AuthProvider
```

### 3.6 이메일·비밀번호 인증 관리자

- [ ] `DaoAuthenticationProvider`를 구성한다.
- [ ] `CustomUserDetailsService`를 연결한다.
- [ ] Argon2 `PasswordEncoder`를 연결한다.
- [ ] Controller/Service에서 사용할 `AuthenticationManager` Bean을 노출한다.

```text
AuthenticationManager
   ↓
DaoAuthenticationProvider
   ├── CustomUserDetailsService
   └── PasswordEncoder
```

### 3.7 인증·인가 예외 응답

- [ ] 인증 실패를 처리하는 `AuthenticationEntryPoint`를 구현한다.
- [ ] 권한 부족을 처리하는 `AccessDeniedHandler`를 구현한다.
- [ ] 프로젝트 공통 응답 형식으로 JSON을 반환한다.

```text
인증 실패 → 401 Unauthorized
권한 부족 → 403 Forbidden
```

응답 예시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_001",
    "message": "인증이 필요합니다."
  }
}
```

### 3.8 SecurityFilterChain

- [ ] 공개 API에 `permitAll`을 적용한다.
- [ ] 일반 API에 `authenticated`를 적용한다.
- [ ] `/admin/**`에 `hasRole("ADMIN")`을 적용한다.
- [ ] Resource Server JWT 검증을 활성화한다.
- [ ] JWT의 `roles` 클레임을 Spring Security Authority로 변환한다.
- [ ] API와 OAuth2 로그인 세션 정책을 분리한다.

권장 Filter Chain 구성:

```text
OAuth2SecurityFilterChain
├── /oauth2/**
├── /login/oauth2/**
└── SessionCreationPolicy.IF_REQUIRED

ApiSecurityFilterChain
├── REST API
├── SessionCreationPolicy.STATELESS
└── OAuth2 Resource Server JWT
```

OAuth2 로그인은 인증 요청의 `state`를 보관해야 하므로 모든 요청에 `STATELESS`를 일괄 적용하지 않는다.

---

## 4. CSRF 처리 주의사항

CORS 설정을 보류하더라도 CSRF 정책은 별도로 결정해야 한다.

```text
CORS: 다른 Origin의 브라우저 요청 허용 정책
CSRF: 브라우저의 자동 쿠키 전송을 이용한 위조 요청 방어
```

- [ ] Access Token API는 `Authorization` 헤더를 사용한다.
- [ ] Refresh Token은 Secure/HttpOnly 쿠키로 전달한다.
- [ ] `/auth/token/refresh`, `/auth/logout`에 CSRF 보호를 적용한다.
- [ ] 최종 설정에서 CSRF를 아무 조건 없이 전역 비활성화하지 않는다.
- [ ] SPA CSRF 토큰 발급 및 `X-XSRF-TOKEN` 헤더 전달 방식을 구현한다.

JWT 기본 설정 단계에서 CSRF를 임시 비활성화했다면 Refresh Token API 구현 전에 반드시 제거하거나 보호 범위를 재설정한다.

---

## 5. 기본 설정 이후 구현 순서

- [ ] LOCAL 회원가입 구현
- [ ] LOCAL 로그인 및 Access Token 발급 구현
- [ ] Refresh Token 생성·해시 저장·쿠키 전달 구현
- [ ] Refresh Token Rotation과 재사용 탐지 구현
- [ ] 로그아웃 및 전체 세션 폐기 구현
- [ ] Google OAuth2 로그인 구현
- [ ] 카카오 OAuth2 로그인 구현
- [ ] OAuth 일회용 코드 교환 구현
- [ ] 메서드 단위 권한 검사(`@PreAuthorize`) 적용

OAuth 가입 예외 정책은 기존 명세를 따른다.

```text
OAuth 이메일 없음 또는 미검증 → 가입 실패
닉네임 없음 또는 중복 → 임시 닉네임 생성 후 프로필 수정 유도
다른 Provider의 동일 이메일 → 자동 연결 없이 가입 실패 및 기존 방식 안내
```

---

## 6. 테스트 체크리스트

- [ ] JWT Secret 누락 시 애플리케이션 시작 실패
- [ ] 32바이트 미만 JWT Secret 사용 시 시작 실패
- [ ] HS256 이외 알고리즘으로 생성한 JWT 거부
- [ ] 잘못된 issuer 또는 audience를 가진 JWT 거부
- [ ] 만료된 JWT 요청에 401 반환
- [ ] 토큰 없이 보호 API 접근 시 401 반환
- [ ] USER가 관리자 API 접근 시 403 반환
- [ ] ADMIN이 관리자 API 접근 가능
- [ ] LOCAL 사용자 비밀번호 일치·불일치 테스트
- [ ] KAKAO·GOOGLE 사용자 비밀번호 로그인 차단
- [ ] Refresh Token 원문이 DB와 로그에 저장되지 않는지 확인
- [ ] 로그아웃 후 Refresh Token 재사용 차단
- [ ] 회전된 이전 Refresh Token 재사용 시 토큰 패밀리 전체 폐기
- [ ] OAuth2 로그인 흐름에서 임시 세션과 콜백 정상 동작

---

## 7. 전체 진행 순서 요약

```text
1. JWT_SECRET 생성
2. OAuth2 Client·Resource Server 의존성 활성화
3. Argon2 구현 의존성 준비
4. AuthProperties 작성
5. PasswordEncoder 작성
6. SecretKey·JwtEncoder·JwtDecoder 작성
7. JwtTokenService 작성
8. CustomUserPrincipal·UserDetailsService 작성
9. 401·403 예외 처리 작성
10. SecurityFilterChain 작성
11. 기본 보안 설정 테스트
12. LOCAL 회원가입·로그인 구현
13. Refresh Token 구현
14. Google·카카오 OAuth2 구현
```
