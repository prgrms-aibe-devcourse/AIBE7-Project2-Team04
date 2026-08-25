package org.example.project2.domain.auth.service.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.auth.entity.RefreshToken;
import org.example.project2.domain.auth.repository.RefreshTokenRepository;
import org.example.project2.domain.auth.exception.InvalidRefreshTokenException;
import org.example.project2.domain.auth.exception.RefreshTokenReuseDetectedException;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.security.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final RefreshTokenHasher tokenHasher;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issue(User user) {
        Instant now = clock.instant();

        // 동시 로그인 시 세션 수 일관성을 위해 사용자 행에 비관적 락을 획득
        if (user.getId() != null) {
            userRepository.findByIdForUpdate(user.getId());
        }

        // 활성 세션(family) 조회 및 최대 허용 세션 수 초과분 폐기
        int maxSessions = authProperties.jwt().maxActiveSessions();
        List<UUID> activeFamilyIds = refreshTokenRepository.findActiveFamilyIdsOrderByOldest(user.getId(), now);
        int excessCount = activeFamilyIds.size() - maxSessions + 1;
        if (excessCount > 0) {
            List<UUID> familiesToRevoke = activeFamilyIds.subList(0, excessCount);
            refreshTokenRepository.revokeActiveFamilies(familiesToRevoke, now);
        }

        String rawToken = tokenGenerator.generate();
        RefreshToken refreshToken = newRefreshToken(user, rawToken, UUID.randomUUID(), now);
        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(rawToken, refreshToken.getExpiresAt());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken currentToken = findForUpdate(rawToken);

        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeActiveFamily(currentToken.getFamilyId(), now);
            throw new RefreshTokenReuseDetectedException();
        }

        if (currentToken.isExpired(now)) {
            currentToken.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        String replacementRawToken = tokenGenerator.generate();
        RefreshToken replacement = newRefreshToken(
                currentToken.getUser(),
                replacementRawToken,
                currentToken.getFamilyId(),
                now
        );
        refreshTokenRepository.save(replacement);
        currentToken.rotateTo(replacement, now);

        return new RotatedRefreshToken(
                replacementRawToken,
                replacement.getExpiresAt(),
                currentToken.getUser().getId(),
                currentToken.getUser().getRole()
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public int purgeTokensExpiredBefore(Instant cutoff) {
        refreshTokenRepository.clearReplacedByTokenForExpiredBefore(cutoff);
        int deletedCount = refreshTokenRepository.deleteExpiredBefore(cutoff);
        log.info("만료된 Refresh Token 정리 완료: cutoff={}, count={}", cutoff, deletedCount);
        return deletedCount;
    }

    private RefreshToken findForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        return refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private RefreshToken newRefreshToken(
            User user,
            String rawToken,
            UUID familyId,
            Instant now
    ) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .familyId(familyId)
                .expiresAt(now.plus(authProperties.jwt().refreshTokenExpiry()))
                .build();
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedRefreshToken[rawToken=[REDACTED], expiresAt=" + expiresAt + "]";
        }
    }

    public record RotatedRefreshToken(
            String rawToken,
            Instant expiresAt,
            UUID userId,
            UserRole role
    ) {
        @Override
        public String toString() {
            return "RotatedRefreshToken[rawToken=[REDACTED], expiresAt=" + expiresAt
                    + ", userId=" + userId + ", role=" + role + "]";
        }
    }
}
