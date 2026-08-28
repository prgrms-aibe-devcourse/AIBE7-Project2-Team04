package org.example.project2.domain.matching.service.proposal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchParticipant;
import org.example.project2.domain.matching.entity.MatchParticipantRole;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.request.RealtimeMatchRedisLifecycleService;
import org.example.project2.domain.matching.service.result.MatchResultResponseAssembler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MatchProposalLifecycleService {
    /**
     * 제안 락(Lock) 쿼리가 영속성 컨텍스트(1차 캐시)에서 이미 관리 중인 요청 인스턴스를 반환할 수 있습니다
     * 이 경우 다른 트랜잭션이 커밋하기 전에 읽었던 이전 상태가 하이버네이트에 그대로 유지되어 있을 수 있습니다.
     * 따라서 락이 걸린 엔티티를 새로고침(Refresh)하여, 이후의 상태 검증이 DB 락으로 보호되는 최신 값을 사용하도록 보장합니다.
     */
    @PersistenceContext
    private EntityManager entityManager;

    private final MatchProposalRepository matchProposalRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchResultResponseAssembler matchResultResponseAssembler;
    private final ApplicationEventPublisher eventPublisher;
    private final RealtimeMatchRedisLifecycleService redisLifecycleService;
    private final Clock clock;

    /**
     * 제안 행을 잠근 뒤 요청 ID 오름차순으로 두 요청을 잠급니다.
     * 모든 최종 상태 변경 경로가 같은 순서를 사용하므로 교착 가능성을 줄입니다.
     */
    @Transactional
    public MatchProposal decide(
            Long proposalId,
            Long requestId,
            MatchProposalDecision decision,
            Instant decidedAt
    ) {
        MatchProposal proposal = findForUpdate(proposalId);
        LockedRequests lockedRequests = lockProposalRequests(proposal);

        // 종료된 제안에 같은 결정을 재전송한 경우에는 기존 결과를 그대로 반환합니다.
        if (proposal.getStatus() != MatchProposalStatus.PENDING
                && proposal.getDecisionFor(requestId) == decision) {
            return proposal;
        }

        proposal.decide(requestId, decision, decidedAt);
        if (proposal.isAnyRejected()) {
            returnRequestsToWaiting(proposal);
            redisLifecycleService.removeProposalAfterCommit(proposal.getId());
        } else if (proposal.isBothAccepted()) {
            completeMatchLocked(proposal, lockedRequests, decidedAt);
        }
        return proposal;
    }

    @Transactional
    public MatchProposal expire(Long proposalId, Instant now) {
        MatchProposal proposal = findForUpdate(proposalId);
        lockProposalRequests(proposal);
        if (!proposal.isExpired(now)) {
            throw new IllegalStateException("아직 응답 제한 시간이 지나지 않은 후보 제안입니다.");
        }
        proposal.expire();
        returnRequestsToWaiting(proposal);
        redisLifecycleService.removeProposalAfterCommit(proposal.getId());
        return proposal;
    }

    @Transactional
    public MatchProposal cancelForRequest(Long proposalId, Long cancelledRequestId) {
        MatchProposal proposal = findForUpdate(proposalId);
        lockProposalRequests(proposal);
        if (!proposal.involvesRequest(cancelledRequestId)) {
            throw new IllegalArgumentException("해당 후보 제안에 포함되지 않은 매칭 요청입니다. " + cancelledRequestId);
        }
        proposal.cancel();

        MatchRequest cancelledRequest = cancelledRequestId.equals(proposal.getRequest1().getId())
                ? proposal.getRequest1()
                : proposal.getRequest2();
        MatchRequest otherRequest = proposal.getOtherRequest(cancelledRequestId);
        cancelledRequest.cancel();
        returnToWaitingIfConfirming(otherRequest);
        redisLifecycleService.removeWaitingAfterCommit(cancelledRequest);
        if (otherRequest.isWaiting()) {
            redisLifecycleService.restoreWaitingAfterCommit(otherRequest);
        }
        redisLifecycleService.removeProposalAfterCommit(proposal.getId());
        return proposal;
    }

    /**
     * 양쪽 수락 이후 Match, 참여자 2명, ChatRoom을 같은 DB 트랜잭션에서 생성합니다.
     * 이미 MATCHED인 제안은 생성 작업을 반복하지 않아 멱등적으로 처리합니다.
     */
    @Transactional
    public MatchProposal completeMatch(Long proposalId) {
        MatchProposal proposal = findForUpdate(proposalId);
        if (proposal.getStatus() == MatchProposalStatus.MATCHED) {
            return proposal;
        }
        LockedRequests lockedRequests = lockProposalRequests(proposal);
        return completeMatchLocked(proposal, lockedRequests, clock.instant());
    }

    private MatchProposal completeMatchLocked(
            MatchProposal proposal,
            LockedRequests lockedRequests,
            Instant matchedAt
    ) {
        if (proposal.getStatus() == MatchProposalStatus.MATCHED) {
            return proposal;
        }
        if (proposal.getStatus() != MatchProposalStatus.PENDING) {
            throw new IllegalStateException("진행 중인 제안만 매칭 확정 처리할 수 있습니다. 현재 상태: " + proposal.getStatus());
        }
        if (!proposal.isBothAccepted()) {
            throw new IllegalStateException("양쪽 사용자가 모두 수락한 제안만 매칭 확정 처리할 수 있습니다.");
        }

        MatchRequest request1 = lockedRequests.request1();
        MatchRequest request2 = lockedRequests.request2();
        Optional<Match> existingPair = safeOptional(matchRepository.findByRequestPair(
                request1.getId(), request2.getId()
        ));
        if (existingPair.isPresent()) {
            // 다른 재시도에서 이미 같은 쌍이 생성된 경우에는 중복 레코드를 만들지 않습니다.
            if (request1.isConfirming()) {
                request1.match();
            }
            if (request2.isConfirming()) {
                request2.match();
            }
            proposal.match();
            removeRealtimeState(proposal, request1, request2);
            return proposal;
        }

        Optional<Match> existingForRequest1 = safeOptional(
                matchRepository.findByRequestId(request1.getId())
        );
        Optional<Match> existingForRequest2 = safeOptional(
                matchRepository.findByRequestId(request2.getId())
        );
        if (existingForRequest1.isPresent() || existingForRequest2.isPresent()) {
            // 한 요청이 다른 쌍으로 먼저 확정된 경우에는 이 제안을 후보 없음으로 종료합니다.
            proposal.cancel();
            returnToWaitingIfConfirming(request1);
            returnToWaitingIfConfirming(request2);
            removeAndRestoreWaitingState(proposal, request1, request2);
            return proposal;
        }

        // 다른 제안으로 이미 매칭되었거나 취소·만료된 요청은 경쟁에서 패한 제안으로 종료합니다.
        if (!request1.isConfirming() || !request2.isConfirming()) {
            proposal.cancel();
            returnToWaitingIfConfirming(request1);
            returnToWaitingIfConfirming(request2);
            removeAndRestoreWaitingState(proposal, request1, request2);
            return proposal;
        }
        if (matchedAt == null) {
            throw new IllegalArgumentException("매칭 성사 시각은 필수입니다.");
        }

        Match match = Match.of(request1, request2, matchedAt);
        request1.match();
        request2.match();
        proposal.match();

        Match savedMatch = matchRepository.save(match);
        if (savedMatch == null || savedMatch.getId() == null) {
            throw new IllegalStateException("매칭 저장 결과를 확인할 수 없습니다.");
        }
        matchParticipantRepository.saveAll(List.of(
                MatchParticipant.builder()
                        .match(savedMatch)
                        .user(request1.getUser())
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build(),
                MatchParticipant.builder()
                        .match(savedMatch)
                        .user(request2.getUser())
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build()
        ));

        ChatRoom savedChatRoom = chatRoomRepository.save(
                ChatRoom.builder().match(savedMatch).build()
        );
        if (savedChatRoom == null || savedChatRoom.getId() == null) {
            throw new IllegalStateException("채팅방 저장 결과를 확인할 수 없습니다.");
        }

        publishMatchResult(proposal, savedMatch, savedChatRoom);
        removeRealtimeState(proposal, request1, request2);
        return proposal;
    }

    private void publishMatchResult(MatchProposal proposal, Match match, ChatRoom chatRoom) {
        MatchResultResponseAssembler.MatchResultViews views =
                matchResultResponseAssembler.assemble(proposal, match, chatRoom);
        eventPublisher.publishEvent(new MatchResultCreatedEvent(
                views.request1UserId(),
                views.request1Response(),
                views.request2UserId(),
                views.request2Response()
        ));
    }

    private MatchProposal findForUpdate(Long proposalId) {
        if (proposalId == null) {
            throw new IllegalArgumentException("후보 제안 ID는 필수입니다.");
        }
        return matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("후보 제안을 찾을 수 없습니다: " + proposalId));
    }

    private LockedRequests lockProposalRequests(MatchProposal proposal) {
        List<Long> requestIds = List.of(
                        proposal.getRequest1().getId(),
                        proposal.getRequest2().getId()
                ).stream()
                .sorted()
                .toList();
        List<MatchRequest> locked = matchRequestRepository.findAllByIdInForUpdate(requestIds);
        if (locked == null || locked.size() != 2) {
            throw new IllegalStateException("매칭 확정에 필요한 요청 잠금에 실패했습니다.");
        }
        refreshLockedRequests(locked);
        MatchRequest request1 = locked.stream()
                .filter(request -> requestIds.get(0).equals(request.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("첫 번째 매칭 요청 잠금에 실패했습니다."));
        MatchRequest request2 = locked.stream()
                .filter(request -> requestIds.get(1).equals(request.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("두 번째 매칭 요청 잠금에 실패했습니다."));
        return new LockedRequests(request1, request2);
    }

    private void refreshLockedRequests(List<MatchRequest> lockedRequests) {
        if (entityManager == null) {
            // Unit tests construct this service without a persistence context.
            return;
        }
        lockedRequests.forEach(request ->
                entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE));
    }

    private void returnRequestsToWaiting(MatchProposal proposal) {
        returnToWaitingIfConfirming(proposal.getRequest1());
        returnToWaitingIfConfirming(proposal.getRequest2());
        if (proposal.getRequest1().isWaiting() && proposal.getRequest2().isWaiting()) {
            redisLifecycleService.restoreWaitingPairAfterCommit(
                    proposal.getRequest1(), proposal.getRequest2()
            );
        } else {
            if (proposal.getRequest1().isWaiting()) {
                redisLifecycleService.restoreWaitingAfterCommit(proposal.getRequest1());
            }
            if (proposal.getRequest2().isWaiting()) {
                redisLifecycleService.restoreWaitingAfterCommit(proposal.getRequest2());
            }
        }
    }

    private void returnToWaitingIfConfirming(MatchRequest request) {
        if (request.isConfirming()) {
            request.returnToWaiting();
        }
    }

    private void removeRealtimeState(
            MatchProposal proposal,
            MatchRequest request1,
            MatchRequest request2
    ) {
        redisLifecycleService.removeWaitingAfterCommit(request1, request2);
        redisLifecycleService.removeProposalAfterCommit(proposal.getId());
    }

    /**
     * 경쟁에서 패한 제안을 종료하면서 아직 유효한 WAITING 요청만 Redis에 복귀시킵니다.
     */
    private void removeAndRestoreWaitingState(
            MatchProposal proposal,
            MatchRequest request1,
            MatchRequest request2
    ) {
        redisLifecycleService.removeWaitingAfterCommit(request1, request2);
        if (request1.isWaiting() && request2.isWaiting()) {
            redisLifecycleService.restoreWaitingPairAfterCommit(request1, request2);
        } else {
            if (request1.isWaiting()) {
                redisLifecycleService.restoreWaitingAfterCommit(request1);
            }
            if (request2.isWaiting()) {
                redisLifecycleService.restoreWaitingAfterCommit(request2);
            }
        }
        redisLifecycleService.removeProposalAfterCommit(proposal.getId());
    }

    private <T> Optional<T> safeOptional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private record LockedRequests(MatchRequest request1, MatchRequest request2) {
    }
}
