package org.example.project2.domain.matching.dto.history;

import java.time.Instant;

import org.example.project2.domain.matching.entity.MatchStatus;

public record MatchHistoryResponse(
        Long matchId,
        MatchStatus status,
        Instant matchedAt,
        String partnerNickname,
        String partnerProfileImageUrl,
        String regionName,
        String foodCategory,
        Instant mealAt,
        boolean reviewed,
        boolean reported
) {
}
