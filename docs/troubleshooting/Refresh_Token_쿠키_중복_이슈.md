# [Troubleshooting] Refresh Token 쿠키 중복 발급 및 세션 꼬임 이슈

## 1. 증상 (Symptom)

- 사용자가 로그인 또는 토큰 재발급(`/auth/token/refresh`)을 진행할 때 브라우저 개발자 도구(Application -> Cookies)에 동일한 이름(`refreshToken`)의 쿠키가 2개 생성되는 현상 발생.
- API 요청 시 HTTP `Cookie` 헤더에 2개의 `refreshToken` 쿠키가 동시에 담겨 전송됨.
- 이로 인해 토큰 회전(RTR, Refresh Token Rotation) 처리 과정에서 어떤 쿠키 값이 검증 대상인지 불명확해지고, 멱등성이 파기되어 유효한 세션이 강제 폐기(Revoked)되거나 `401 Unauthorized` 예외가 지속적으로 발생하는 문제 발생.

---

## 2. 원인 (Cause)

### ① RFC 6265 쿠키 식별 메커니즘
- 브라우저는 **쿠키 이름(`Name`) + 경로(`Path`)**의 조합을 고유 식별키로 사용함.

### ② 쿠키 Path 변경에 따른 구버전 쿠키 잔재
- 서비스 초기에는 Refresh Token 쿠키의 경로가 `Path=/auth`로 설정되어 있었으나, 인증 구조 개선 및 전역 접근성을 위해 경로를 `Path=/`로 변경함.
- 새로운 `Path=/` 쿠키가 발급되더라도, 브라우저는 기존 `Path=/auth` 쿠키를 덮어쓰지 않고 각각 별개의 쿠키로 유지함.

### ③ 브라우저의 전송 조건 충돌
- 클라이언트가 `/auth/token/refresh` 등 `/auth` 하위 엔드포인트로 요청을 보낼 때, 브라우저는 `Path=/auth`와 `Path=/` 두 쿠키가 모두 해당 요청 경로에 부합하다고 판단하여 **2개의 `refreshToken` 쿠키를 HTTP 요청 헤더에 한 번에 담아서 전송**함.

---

## 3. 해결 (Solution)

### ① 구버전 레거시 쿠키 명시적 강제 삭제 (`AuthCookieUtil`)
- `AuthCookieUtil`에 구버전 경로(`Path=/auth`) 전용 쿠키 삭제 메서드(`deleteLegacyRefreshTokenCookie`)를 신설.
- 토큰 발급, 재발급, 로그아웃 처리 시 `Set-Cookie` 응답 헤더로 `Path=/auth` 쿠키의 `Max-Age=0`을 함께 전달하여 브라우저에 남아있는 구버전 쿠키를 명시적으로 파기함.

```java
// AuthCookieUtil.java
public ResponseCookie deleteLegacyRefreshTokenCookie() {
    return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path("/auth")
            .maxAge(0)
            .build();
}
```

```java
// AuthController.java - refresh 메서드 예시
response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.createAccessTokenCookie(accessToken).toString());
response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.deleteLegacyRefreshTokenCookie().toString()); // 레거시 파기
response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.createRefreshTokenCookie(rotated.rawToken()).toString());
```

### ② 다중 쿠키 수집 및 중복 세션 일괄 폐기 (`AuthController`)
- `HttpServletRequest`에 들어온 모든 쿠키 중 `name="refreshToken"`인 쿠키들을 스트림으로 수집.
- `.distinct()`를 적용하여 중복된 토큰 값을 모두 추출한 뒤, DB상에 존재하는 레거시 세션을 안전하게 폐기(`revoke`) 처리하도록 보장함.

```java
private void revokeRefreshTokens(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return;

    Arrays.stream(cookies)
            .filter(cookie -> "refreshToken".equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(token -> token != null && !token.isBlank())
            .distinct()
            .forEach(refreshTokenService::revoke);
}
```

---

## 4. 성과 및 결론

- 쿠키 Path 변경 시 발생하는 레거시 쿠키 잔재를 파기하여 브라우저 쿠키 중복 발급 문제 완전 해결.
- 토큰 재발급 및 로그아웃 시 401 오류 및 세션 오작동을 차단하여 인증 안정성 확보.
