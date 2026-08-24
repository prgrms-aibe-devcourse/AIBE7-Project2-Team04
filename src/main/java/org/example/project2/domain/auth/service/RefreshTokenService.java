package org.example.project2.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.entity.RefreshToken;
import org.example.project2.domain.auth.repository.RefreshTokenRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.global.security.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final RefreshTokenHasher tokenHasher;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issue(User user) {
        Instant now = clock.instant();
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
