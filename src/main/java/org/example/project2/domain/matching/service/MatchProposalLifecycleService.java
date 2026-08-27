package org.example.project2.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MatchProposalLifecycleService {
    private final MatchProposalRepository matchProposalRepository;

    @Transactional
    public MatchProposal decide(
            Long proposalId,
            Long requestId,
            MatchProposalDecision decision,
            Instant decidedAt
    ) {
        MatchProposal proposal = findForUpdate(proposalId);
        proposal.decide(requestId, decision, decidedAt);
        if (proposal.isAnyRejected()) {
            returnRequestsToWaiting(proposal);
        }
        return proposal;
    }

    @Transactional
    public MatchProposal expire(Long proposalId, Instant now) {
        MatchProposal proposal = findForUpdate(proposalId);
        if (!proposal.isExpired(now)) {
            throw new IllegalStateException("아직 응답 제한 시간이 지나지 않은 후보 제안입니다.");
        }
        proposal.expire();
        returnRequestsToWaiting(proposal);
        return proposal;
    }

    @Transactional
    public MatchProposal cancelForRequest(Long proposalId, Long cancelledRequestId) {
        MatchProposal proposal = findForUpdate(proposalId);
        if (!proposal.involvesRequest(cancelledRequestId)) {
            throw new IllegalArgumentException("해당 후보 제안에 포함되지 않은 매칭 요청입니다: " + cancelledRequestId);
        }
        proposal.cancel();

        MatchRequest cancelledRequest = cancelledRequestId.equals(proposal.getRequest1().getId())
                ? proposal.getRequest1()
                : proposal.getRequest2();
        MatchRequest otherRequest = proposal.getOtherRequest(cancelledRequestId);
        cancelledRequest.cancel();
        returnToWaitingIfConfirming(otherRequest);
        return proposal;
    }

    private MatchProposal findForUpdate(Long proposalId) {
        if (proposalId == null) {
            throw new IllegalArgumentException("후보 제안 ID는 필수입니다.");
        }
        return matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("후보 제안을 찾을 수 없습니다: " + proposalId));
    }

    private void returnRequestsToWaiting(MatchProposal proposal) {
        returnToWaitingIfConfirming(proposal.getRequest1());
        returnToWaitingIfConfirming(proposal.getRequest2());
    }

    private void returnToWaitingIfConfirming(MatchRequest request) {
        if (request.isConfirming()) {
            request.returnToWaiting();
        }
    }
}
