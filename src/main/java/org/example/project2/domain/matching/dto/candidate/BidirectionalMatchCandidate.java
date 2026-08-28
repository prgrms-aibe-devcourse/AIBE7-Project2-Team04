package org.example.project2.domain.matching.dto.candidate;

import java.time.Instant;
import java.util.UUID;

/**
 * 양방향 하드 필터를 통과한 요청 단위 후보입니다.
 *
 * 프로필 제안이 생성되기 전에는 이 정보를 API로 노출하지 않습니다.
 */
public record BidirectionalMatchCandidate(
        Long requestId,
        UUID userId,
        int distanceMeters,
        Instant waitingStartedAt
) {
    public BidirectionalMatchCandidate(
            Long requestId,
            UUID userId,
            int distanceMeters
    ) {
        this(requestId, userId, distanceMeters, null);
    }
}
