package org.example.project2.domain.matching.dto.request;

import org.example.project2.domain.matching.entity.MatchRequestStatus;

import java.time.Instant;

public record RealtimeMatchRequestResponse(
        Long requestId,
        MatchRequestStatus status,
        Instant expiresAt
) {
}
