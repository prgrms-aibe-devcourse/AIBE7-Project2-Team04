package org.example.project2.global.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validSecurityPropertiesPassValidation() {
        AuthProperties properties = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void invalidBase64OrShortSecretIsRejected() {
        Set<ConstraintViolation<AuthProperties>> invalidBase64 = validator.validate(
                properties("not-base64!", Duration.ofMinutes(15), Duration.ofDays(14))
        );
        Set<ConstraintViolation<AuthProperties>> shortSecret = validator.validate(
                properties(
                        Base64.getEncoder().encodeToString(new byte[31]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14)
                )
        );

        assertThat(invalidBase64).isNotEmpty();
        assertThat(shortSecret).isNotEmpty();
    }

    @Test
    void accessTokenExpiryMustBeShorterThanRefreshTokenExpiry() {
        AuthProperties properties = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofDays(14),
                Duration.ofDays(14)
        );

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void maxActiveSessionsMustBeAtLeastOne() {
        AuthProperties invalid = new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        Base64.getEncoder().encodeToString(new byte[32]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        0,
                        Duration.ofDays(7)
                ),
                new AuthProperties.Cors("")
        );

        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    @Test
    void cleanupRetentionMustBePositive() {
        AuthProperties zero = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(15),
                Duration.ofDays(14),
                Duration.ZERO
        );
        AuthProperties negative = properties(
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(15),
                Duration.ofDays(14),
                Duration.ofDays(-1)
        );

        assertThat(validator.validate(zero)).isNotEmpty();
        assertThat(validator.validate(negative)).isNotEmpty();
    }

    private AuthProperties properties(
            String secret,
            Duration accessTokenExpiry,
            Duration refreshTokenExpiry
    ) {
        return properties(secret, accessTokenExpiry, refreshTokenExpiry, Duration.ofDays(7));
    }

    private AuthProperties properties(
            String secret,
            Duration accessTokenExpiry,
            Duration refreshTokenExpiry,
            Duration cleanupRetention
    ) {
        return new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        secret,
                        accessTokenExpiry,
                        refreshTokenExpiry,
                        5,
                        cleanupRetention
                ),
                new AuthProperties.Cors("")
        );
    }
}
