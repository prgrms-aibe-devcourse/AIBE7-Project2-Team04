package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionRequest;
import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.exception.proposal.AuthenticatedMatchProposalUserNotFoundException;
import org.example.project2.domain.matching.exception.proposal.MatchProposalErrorCode;
import org.example.project2.domain.matching.exception.proposal.MatchProposalException;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchProposalInteractionService {
    private final MatchProposalRepository matchProposalRepository;
    private final MatchProposalLifecycleService matchProposalLifecycleService;
    private final UserPersonalityProfileRepository personalityProfileRepository;
    private final Clock clock;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public MatchProposalResponse getCurrent(UUID userId) {
        requireUser(userId);
        MatchProposal proposal = matchProposalRepository.findPendingByUserId(userId)
                .orElseThrow(() -> new MatchProposalException(MatchProposalErrorCode.PROPOSAL_NOT_FOUND));
        MatchRequest viewerRequest = findViewerRequest(proposal, userId);
        if (proposal.isExpired(clock.instant())) {
            throw new MatchProposalException(
                    MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT,
                    "응답 시간이 만료된 후보 제안입니다."
            );
        }
        return toResponse(proposal, viewerRequest);
    }

    @Transactional
    public MatchProposalResponse decide(
            UUID userId,
            Long proposalId,
            MatchProposalDecisionRequest request
    ) {
        requireUser(userId);
        if (proposalId == null || request == null || request.decision() == null) {
            throw new MatchProposalException(MatchProposalErrorCode.INVALID_INPUT);
        }

        MatchProposal proposal = matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new MatchProposalException(MatchProposalErrorCode.PROPOSAL_NOT_FOUND));
        MatchRequest viewerRequest = findViewerRequest(proposal, userId);
        MatchProposalDecision requestedDecision = request.decision().toEntityDecision();
        if (proposal.getDecisionFor(viewerRequest.getId()) == requestedDecision) {
            return toResponse(proposal, viewerRequest);
        }
        if (proposal.getStatus() != MatchProposalStatus.PENDING) {
            throw new MatchProposalException(MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        if (proposal.isExpired(now)) {
            throw new MatchProposalException(
                    MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT,
                    "응답 시간이 만료된 후보 제안입니다."
            );
        }

        MatchProposal updated;
        try {
            updated = matchProposalLifecycleService.decide(
                    proposalId,
                    viewerRequest.getId(),
                    requestedDecision,
                    now
            );
        } catch (IllegalStateException exception) {
            throw new MatchProposalException(
                    MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT,
                    exception.getMessage()
            );
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            // 요청 잠금 경쟁 또는 DB UNIQUE 제약으로 패한 결정은 중복 생성 없이 충돌로 응답합니다.
            throw new MatchProposalException(
                    MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT,
                    "다른 매칭이 먼저 확정되어 현재 제안을 처리할 수 없습니다."
            );
        }
        return toResponse(updated, viewerRequest);
    }

    private MatchRequest findViewerRequest(MatchProposal proposal, UUID userId) {
        MatchRequest request1 = proposal.getRequest1();
        if (request1.getUser().getId().equals(userId)) {
            return request1;
        }
        MatchRequest request2 = proposal.getRequest2();
        if (request2.getUser().getId().equals(userId)) {
            return request2;
        }
        throw new MatchProposalException(MatchProposalErrorCode.PROPOSAL_FORBIDDEN);
    }

    private MatchProposalResponse toResponse(MatchProposal proposal, MatchRequest viewerRequest) {
        MatchRequest partnerRequest = proposal.getOtherRequest(viewerRequest.getId());
        User partner = partnerRequest.getUser();
        Set<PersonalityTag> publicTags = personalityProfileRepository.findByUserId(partner.getId())
                .map(profile -> profile.getStyleTags())
                .orElse(Set.of());
        BidirectionalMatchScoreSnapshot snapshot = proposal.getScoreSnapshot();
        boolean viewerIsRequest1 = viewerRequest.getId().equals(proposal.getRequest1().getId());
        List<String> reasons = snapshot == null
                ? List.of()
                : viewerIsRequest1 ? snapshot.sourceToTargetReasons() : snapshot.targetToSourceReasons();
        List<PersonalityTag> matchedTags = snapshot == null
                ? List.of()
                : viewerIsRequest1
                ? snapshot.sourceToTargetMatchedTags()
                : snapshot.targetToSourceMatchedTags();
        Short score = snapshot == null ? null : snapshot.pairScore();

        return new MatchProposalResponse(
                proposal.getId(),
                proposal.getExpiresAt(),
                proposal.getStatus(),
                proposal.getDecisionFor(viewerRequest.getId()),
                new MatchProposalPartnerProfileResponse(
                        partner.getId(),
                        partner.getNickname(),
                        profileImageUrlResolver.resolve(partner),
                        partner.getDescription(),
                        publicTags
                ),
                score,
                matchedTags,
                reasons
        );
    }

    private void requireUser(UUID userId) {
        if (userId == null) {
            throw new AuthenticatedMatchProposalUserNotFoundException();
        }
    }
}
