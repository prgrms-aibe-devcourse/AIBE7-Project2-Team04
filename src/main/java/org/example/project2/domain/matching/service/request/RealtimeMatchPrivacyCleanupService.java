package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.proposal.MatchProposalLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 위치 동의 철회나 회원 탈퇴처럼 정밀 위치를 더 이상 보관할 수 없는 경우
 * 활성 실시간 매칭 상태와 요청의 개인정보를 함께 정리합니다.
 */
@Service
@RequiredArgsConstructor
public class RealtimeMatchPrivacyCleanupService {
    private static final List<MatchRequestStatus> ACTIVE_STATUSES =
            List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING);

    private final MatchRequestRepository matchRequestRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final MatchProposalLifecycleService matchProposalLifecycleService;
    private final RealtimeMatchRedisLifecycleService redisLifecycleService;

    @Transactional
    public void removeActiveRequests(UUID userId) {
        if (userId == null) {
            return;
        }

        List<MatchRequest> activeRequests = matchRequestRepository
                .findAllByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        for (MatchRequest request : activeRequests) {
            MatchProposal cancelledProposal = cancelRealtimeState(request);
            if (cancelledProposal != null) {
                // JPA가 생성한 FK의 DB CASCADE 설정에 의존하지 않고 참조 행을 먼저 제거한다.
                matchProposalRepository.delete(cancelledProposal);
            }
            // 활성 요청은 이력 보존 대상이 아니므로 정밀 핀, 희망 설명과 임베딩을 함께 물리 삭제한다.
            matchRequestRepository.delete(request);
        }
    }

    private MatchProposal cancelRealtimeState(MatchRequest request) {
        if (request.isConfirming()) {
            MatchProposal proposal = matchProposalRepository
                    .findPendingByRequestId(request.getId())
                    .orElse(null);
            if (proposal != null) {
                return matchProposalLifecycleService.cancelForRequest(proposal.getId(), request.getId());
            }
        }

        if (request.isWaiting() || request.isConfirming()) {
            request.cancel();
        }
        redisLifecycleService.removeWaitingAfterCommit(request);
        return null;
    }
}
