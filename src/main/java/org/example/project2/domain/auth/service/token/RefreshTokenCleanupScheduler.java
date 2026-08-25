package org.example.project2.domain.auth.service.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.global.security.AuthProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Scheduled(cron = "${app.auth.jwt.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public int cleanupExpiredTokens() {
        Instant cutoff = clock.instant().minus(authProperties.jwt().cleanupRetention());
        log.info("만료 Refresh Token 정기 정리 시작 (기준 시각: {})", cutoff);
        int deletedCount = refreshTokenService.purgeTokensExpiredBefore(cutoff);
        log.info("만료 Refresh Token 정기 정리 종료 (삭제 건수: {})", deletedCount);
        return deletedCount;
    }
}
