package org.example.project2.domain.matching.dto.result;

import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.entity.MatchStatus;

/**
 * 양쪽 수락 후 커밋된 매칭 결과입니다.
 * 양쪽이 요청에서 선택한 희망 장소는 채팅 지도용으로 포함하되,
 * 실시간 위치, 자유 텍스트, 임베딩 등 내부 데이터는 포함하지 않습니다.
 */
public record MatchResultResponse(
        Long matchId,
        MatchStatus status,
        Long chatRoomId,
        MatchResultCompatibilityResponse compatibility,
        MatchProposalPartnerProfileResponse partner,
        MatchResultDesiredLocationsResponse desiredLocations
) {
    public MatchResultResponse {
        if (matchId == null || status == null || chatRoomId == null
                || partner == null || desiredLocations == null) {
            throw new IllegalArgumentException("매칭 결과의 필수 정보가 누락되었습니다.");
        }
    }
}
