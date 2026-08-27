package org.example.project2.domain.matching.service.request;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        Duration waitingTtl,
        int defaultSearchRadiusMeters
) {
    public MatchingProperties {
        waitingTtl = waitingTtl == null ? Duration.ofMinutes(5) : waitingTtl;
        defaultSearchRadiusMeters = defaultSearchRadiusMeters == 0 ? 3_000 : defaultSearchRadiusMeters;
        if (waitingTtl.isZero() || waitingTtl.isNegative()) {
            throw new IllegalArgumentException("매칭 대기 TTL은 양수여야 합니다.");
        }
        if (defaultSearchRadiusMeters <= 0) {
            throw new IllegalArgumentException("기본 매칭 탐색 반경은 양수여야 합니다.");
        }
    }
}
