package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * 제안 저장 트랜잭션이 커밋된 뒤 양쪽 사용자에게 프로필 확인 알림을 전달하기 위한 이벤트입니다.
 * 각 사용자는 자신의 관점에서 본 상대 프로필과 호환도 사유를 받습니다.
 */
public record MatchProposalCreatedEvent(
        UUID request1UserId,
        MatchProposalResponse request1Payload,
        UUID request2UserId,
        MatchProposalResponse request2Payload
) {
    public MatchProposalCreatedEvent {
        if (request1UserId == null || request2UserId == null) {
            throw new IllegalArgumentException("제안 알림 수신자 ID는 필수입니다.");
        }
        if (request1Payload == null || request2Payload == null) {
            throw new IllegalArgumentException("제안 알림 내용은 필수입니다.");
        }
        Instant request1ExpiresAt = request1Payload.expiresAt();
        Instant request2ExpiresAt = request2Payload.expiresAt();
        if (request1ExpiresAt == null || !request1ExpiresAt.equals(request2ExpiresAt)) {
            throw new IllegalArgumentException("양쪽 제안 알림의 제한 시간은 동일해야 합니다.");
        }
    }
}
