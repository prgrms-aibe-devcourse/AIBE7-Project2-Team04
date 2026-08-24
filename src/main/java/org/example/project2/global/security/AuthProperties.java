package org.example.project2.global.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "app.auth")
@Validated
public record AuthProperties(
        @Valid @NotNull Password password,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors
) {
    public record Password(
            @NotBlank String encodingId
    ) {
    }

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotBlank String secretKey,
            @NotNull Duration accessTokenExpiry,
            @NotNull Duration refreshTokenExpiry
    ) {
        private static final int HS256_MINIMUM_KEY_BYTES = 32;

        @AssertTrue(message = "JWT secret-key는 Base64로 인코딩된 32바이트 이상의 키여야 합니다.")
        public boolean isSecretKeyValid() {
            if (secretKey == null || secretKey.isBlank()) {
                return true;
            }

            try {
                return Base64.getDecoder().decode(secretKey).length >= HS256_MINIMUM_KEY_BYTES;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        @AssertTrue(message = "JWT 만료시간은 양수이고 Access Token 만료시간이 Refresh Token보다 짧아야 합니다.")
        public boolean isExpiryValid() {
            if (accessTokenExpiry == null || refreshTokenExpiry == null) {
                return true;
            }

            return !accessTokenExpiry.isZero()
                    && !accessTokenExpiry.isNegative()
                    && !refreshTokenExpiry.isZero()
                    && !refreshTokenExpiry.isNegative()
                    && accessTokenExpiry.compareTo(refreshTokenExpiry) < 0;
        }
    }

    public record Cors(
            String allowedOrigin
    ) {
    }
}
