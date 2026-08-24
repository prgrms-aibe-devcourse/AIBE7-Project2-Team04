package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.entity.RefreshToken;
import org.example.project2.domain.auth.repository.RefreshTokenRepository;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.global.security.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private OpaqueTokenGenerator tokenGenerator;

    private RefreshTokenHasher tokenHasher;
    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenHasher = new RefreshTokenHasher();
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                tokenGenerator,
                tokenHasher,
                authProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .passwordHash("{argon2}hash")
                .provider(AuthProvider.LOCAL)
                .nickname("사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void issueStoresOnlyHashAndReturnsRawTokenOnce() {
        String rawToken = "issued-opaque-token";
        when(tokenGenerator.generate()).thenReturn(rawToken);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();

        assertThat(issued.rawToken()).isEqualTo(rawToken);
        assertThat(issued.toString()).doesNotContain(rawToken);
        assertThat(stored.getTokenHash()).isEqualTo(tokenHasher.hash(rawToken));
        assertThat(stored.getTokenHash()).doesNotContain(rawToken);
        assertThat(stored.getFamilyId()).isNotNull();
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void rotateRevokesCurrentTokenAndLinksReplacement() {
        String currentRawToken = "current-opaque-token";
        String replacementRawToken = "replacement-opaque-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken current = activeToken(currentRawToken, familyId);
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(currentRawToken)))
                .thenReturn(Optional.of(current));
        when(tokenGenerator.generate()).thenReturn(replacementRawToken);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(currentRawToken);

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getLastUsedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedByToken()).isNotNull();
        assertThat(current.getReplacedByToken().getFamilyId()).isEqualTo(familyId);
        assertThat(current.getReplacedByToken().getTokenHash())
                .isEqualTo(tokenHasher.hash(replacementRawToken));
        assertThat(rotated.rawToken()).isEqualTo(replacementRawToken);
        assertThat(rotated.toString()).doesNotContain(replacementRawToken);
        assertThat(rotated.userId()).isEqualTo(USER_ID);
        assertThat(rotated.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void reusedTokenRevokesEveryActiveTokenInFamily() {
        String rawToken = "already-rotated-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = activeToken(rawToken, familyId);
        reused.revoke(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken)))
                .thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);
        verify(refreshTokenRepository).revokeActiveFamily(familyId, NOW);
    }

    private RefreshToken activeToken(String rawToken, UUID familyId) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .familyId(familyId)
                .expiresAt(NOW.plus(Duration.ofDays(1)))
                .build();
    }

    private AuthProperties authProperties() {
        return new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        Base64.getEncoder().encodeToString(new byte[32]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14)
                ),
                new AuthProperties.Cors("")
        );
    }
}
