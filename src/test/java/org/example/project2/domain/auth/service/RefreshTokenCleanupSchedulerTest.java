package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.service.token.RefreshTokenCleanupScheduler;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.global.security.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-25T04:00:00Z");

    @Mock
    private RefreshTokenService refreshTokenService;

    private RefreshTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        Base64.getEncoder().encodeToString(new byte[32]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        5,
                        Duration.ofDays(7)
                ),
                new AuthProperties.Cors("")
        );

        scheduler = new RefreshTokenCleanupScheduler(
                refreshTokenService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void cleanupExpiredTokensCalculatesCutoffAndDelegatesToService() {
        Instant expectedCutoff = NOW.minus(Duration.ofDays(7));
        when(refreshTokenService.purgeTokensExpiredBefore(expectedCutoff)).thenReturn(5);

        int deletedCount = scheduler.cleanupExpiredTokens();

        verify(refreshTokenService).purgeTokensExpiredBefore(expectedCutoff);
        assertThat(deletedCount).isEqualTo(5);
    }
}
