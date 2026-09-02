package org.example.project2.domain.matching.dto.result;

/**
 * 매칭 결과를 조회하는 사용자 기준의 양쪽 희망 장소입니다.
 */
public record MatchResultDesiredLocationsResponse(
        MatchResultLocationResponse mine,
        MatchResultLocationResponse partner
) {
    public MatchResultDesiredLocationsResponse {
        if (mine == null || partner == null) {
            throw new IllegalArgumentException("양쪽 사용자의 희망 장소는 필수입니다.");
        }
    }
}
