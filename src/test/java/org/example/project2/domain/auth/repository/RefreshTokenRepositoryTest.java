package org.example.project2.domain.auth.repository;

import org.example.project2.domain.auth.entity.RefreshToken;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;
    private final Instant now = Instant.parse("2026-08-25T12:00:00Z");

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(User.builder()
                .email("user1@example.com")
                .passwordHash("hashed")
                .nickname("유저1")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        user2 = userRepository.save(User.builder()
                .email("user2@example.com")
                .passwordHash("hashed")
                .nickname("유저2")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void findActiveFamilyIdsOrderByOldestReturnsOnlyActiveDistinctFamiliesOrderedByActivity() {
        UUID familyOldest = UUID.randomUUID();
        UUID familyMiddle = UUID.randomUUID();
        UUID familyNewest = UUID.randomUUID();
        UUID familyExpired = UUID.randomUUID();
        UUID familyRevoked = UUID.randomUUID();
        UUID familyOtherUser = UUID.randomUUID();

        // 1. 가장 오래된 세션
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-oldest")
                .familyId(familyOldest)
                .expiresAt(now.plusSeconds(3600))
                .lastUsedAt(now.minusSeconds(300))
                .build());

        // 2. 중간 세션: 회전 이력이 있어 2개의 토큰이 존재하지만 동일 family_id
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-mid-1")
                .familyId(familyMiddle)
                .expiresAt(now.plusSeconds(3600))
                .revokedAt(now.minusSeconds(100))
                .lastUsedAt(now.minusSeconds(100))
                .createdAt(now.minusSeconds(400))
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-mid-2")
                .familyId(familyMiddle)
                .expiresAt(now.plusSeconds(3600))
                .lastUsedAt(now.minusSeconds(100)) // 최신 활동 시각 now - 100
                .createdAt(now.minusSeconds(100))
                .build());

        // 3. 가장 최신 세션
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-newest")
                .familyId(familyNewest)
                .expiresAt(now.plusSeconds(3600))
                .lastUsedAt(now.minusSeconds(10)) // 최신 활동 시각 now - 10
                .createdAt(now.minusSeconds(10))
                .build());

        // 4. 만료된 세션
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-expired")
                .familyId(familyExpired)
                .expiresAt(now.minusSeconds(10))
                .createdAt(now.minusSeconds(500))
                .build());

        // 5. 이미 폐기된 세션
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-revoked")
                .familyId(familyRevoked)
                .expiresAt(now.plusSeconds(3600))
                .revokedAt(now.minusSeconds(50))
                .createdAt(now.minusSeconds(500))
                .build());

        // 6. 다른 사용자의 세션
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user2)
                .tokenHash("hash-user2")
                .familyId(familyOtherUser)
                .expiresAt(now.plusSeconds(3600))
                .createdAt(now.minusSeconds(600))
                .build());

        List<UUID> activeFamilies = refreshTokenRepository.findActiveFamilyIdsOrderByOldest(user1.getId(), now);

        assertThat(activeFamilies).containsExactly(familyOldest, familyMiddle, familyNewest);
    }

    @Test
    void revokeActiveFamiliesRevokesOnlySpecifiedActiveTokens() {
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();

        RefreshToken token1 = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-f1")
                .familyId(family1)
                .expiresAt(now.plusSeconds(3600))
                .build());

        RefreshToken token2 = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-f2")
                .familyId(family2)
                .expiresAt(now.plusSeconds(3600))
                .build());

        int revokedCount = refreshTokenRepository.revokeActiveFamilies(List.of(family1), now);

        assertThat(revokedCount).isEqualTo(1);

        RefreshToken reloaded1 = refreshTokenRepository.findById(token1.getId()).orElseThrow();
        RefreshToken reloaded2 = refreshTokenRepository.findById(token2.getId()).orElseThrow();

        assertThat(reloaded1.isRevoked()).isTrue();
        assertThat(reloaded1.getRevokedAt()).isEqualTo(now);
        assertThat(reloaded2.isRevoked()).isFalse();
    }

    @Test
    void purgeExpiredTokensDeletesOnlyExpiredBeforeCutoffAndHandlesSelfReferencingFk() {
        Instant cutoff = now.minusSeconds(86400 * 7); // 7일 전

        // 1. 오래된 만료 토큰 (삭제 대상) - 회전되어 2번째 토큰을 replacedByToken으로 참조
        RefreshToken oldExpired2 = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-old-exp-2")
                .familyId(UUID.randomUUID())
                .expiresAt(cutoff.minusSeconds(10))
                .build());

        RefreshToken oldExpired1 = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-old-exp-1")
                .familyId(UUID.randomUUID())
                .expiresAt(cutoff.minusSeconds(100))
                .replacedByToken(oldExpired2)
                .build());

        // 2. 최근 만료 토큰 (보관 기간 이내 -> 보존 대상)
        RefreshToken recentExpired = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-recent-exp")
                .familyId(UUID.randomUUID())
                .expiresAt(cutoff.plusSeconds(3600))
                .build());

        // 3. 만료되지 않은 폐기 토큰 (보존 대상)
        RefreshToken revokedUnexpired = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-revoked-unexp")
                .familyId(UUID.randomUUID())
                .expiresAt(now.plusSeconds(3600))
                .revokedAt(now.minusSeconds(100))
                .build());

        // 4. 유효한 활성 토큰 (보존 대상)
        RefreshToken activeToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user1)
                .tokenHash("hash-active")
                .familyId(UUID.randomUUID())
                .expiresAt(now.plusSeconds(3600))
                .build());

        // 먼저 FK 참조 해제 후 삭제 실행
        refreshTokenRepository.clearReplacedByTokenForExpiredBefore(cutoff);
        int deletedCount = refreshTokenRepository.deleteExpiredBefore(cutoff);

        assertThat(deletedCount).isEqualTo(2);

        assertThat(refreshTokenRepository.findById(oldExpired1.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldExpired2.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(recentExpired.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(revokedUnexpired.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(activeToken.getId())).isPresent();
    }
}
