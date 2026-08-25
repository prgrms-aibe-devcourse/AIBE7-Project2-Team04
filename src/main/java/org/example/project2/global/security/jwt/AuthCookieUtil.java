package org.example.project2.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.security.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class AuthCookieUtil {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String CSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String AUTH_COOKIE_PATH = "/";
    private static final String LEGACY_AUTH_COOKIE_PATH = "/auth";

    private final AuthProperties p;

    public ResponseCookie createRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(AUTH_COOKIE_PATH)
                .maxAge(p.jwt().refreshTokenExpiry())
                .build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return deleteRefreshTokenCookie(AUTH_COOKIE_PATH);
    }

    public ResponseCookie deleteLegacyRefreshTokenCookie() {
        return deleteRefreshTokenCookie(LEGACY_AUTH_COOKIE_PATH);
    }

    private ResponseCookie deleteRefreshTokenCookie(String path) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(path)
                .maxAge(0)
                .build();
    }

    public ResponseCookie deleteLegacyCsrfTokenCookie() {
        return ResponseCookie.from(CSRF_TOKEN_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(false)
                .sameSite("Lax")
                .path(LEGACY_AUTH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    public ResponseCookie createAccessTokenCookie(String token) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(p.jwt().accessTokenExpiry())
                .build();
    }

    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

}
