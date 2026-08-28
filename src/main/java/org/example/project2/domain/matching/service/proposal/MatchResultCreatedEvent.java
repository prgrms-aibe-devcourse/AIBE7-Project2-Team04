package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.entity.MatchStatus;

import java.util.UUID;

/**
 * 매칭 관련 DB 트랜잭션이 커밋된 뒤 각 사용자에게 결과를 전달하기 위한 이벤트입니다.
 */
public record MatchResultCreatedEvent(
        UUID request1UserId,
        MatchResultResponse request1Payload,
        UUID request2UserId,
        MatchResultResponse request2Payload
) {
    public MatchResultCreatedEvent {
        if (request1UserId == null || request2UserId == null
                || request1Payload == null || request2Payload == null) {
            throw new IllegalArgumentException("매칭 결과 알림의 필수 정보가 누락되었습니다.");
        }
        if (request1UserId.equals(request2UserId)) {
            throw new IllegalArgumentException("같은 사용자에게 양방향 매칭 결과를 보낼 수 없습니다.");
        }
        if (request1Payload.status() != MatchStatus.MATCHED
                || request2Payload.status() != MatchStatus.MATCHED) {
            throw new IllegalArgumentException("매칭 완료 상태의 결과만 알림으로 보낼 수 있습니다.");
        }
        if (!request1Payload.matchId().equals(request2Payload.matchId())
                || !request1Payload.chatRoomId().equals(request2Payload.chatRoomId())) {
            throw new IllegalArgumentException("양쪽 매칭 결과의 매칭 ID와 채팅방 ID가 일치해야 합니다.");
        }
    }
}
