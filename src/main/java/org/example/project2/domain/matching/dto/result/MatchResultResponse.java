package org.example.project2.domain.matching.dto.result;

import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.entity.MatchStatus;

/**
 * 양쪽 수락 후 커밋된 매칭 결과입니다.
 * 정밀 위치, 자유 텍스트, 임베딩 등 내부 데이터는 포함하지 않습니다.
 */
public record MatchResultResponse(
        Long matchId,
        MatchStatus status,
        Long chatRoomId,
        MatchResultCompatibilityResponse compatibility,
        MatchProposalPartnerProfileResponse partner
) {
    public MatchResultResponse {
        if (matchId == null || status == null || chatRoomId == null || partner == null) {
            throw new IllegalArgumentException("매칭 결과의 필수 정보가 누락되었습니다.");
        }
    }
}
