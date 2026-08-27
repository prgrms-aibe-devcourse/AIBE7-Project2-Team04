package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot;
import org.example.project2.domain.matching.dto.scoring.DimensionMatchPreference;
import org.example.project2.domain.matching.dto.scoring.MatchingPreferenceSnapshot;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.matching.service.candidate.BidirectionalCandidateSearchService;
import org.example.project2.domain.personality.dto.PersonalityScoresResponse;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchProposalSelectionService {
    public static final String FORMULA_VERSION = "PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1";

    private static final short BASE_CONDITION_SCORE = 50;
    private static final Duration PROPOSAL_TTL = Duration.ofSeconds(15);
    private static final String FALLBACK_REASON = "성향 정보가 부족해 기본 조건을 기준으로 제안했어요.";

    private final BidirectionalCandidateSearchService candidateSearchService;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final UserPersonalityProfileRepository personalityProfileRepository;
    private final UserPersonalityEmbeddingRepository personalityEmbeddingRepository;
    private final PersonalityCompatibilityCalculator personalityCompatibilityCalculator;

    @Transactional
    public Optional<MatchProposal> selectAndCreate(UUID userId, Long sourceRequestId) {
        List<ScoredCandidate> rankedCandidates = candidateSearchService.findCandidates(userId, sourceRequestId)
                .stream()
                .map(candidate -> scoreCandidate(sourceRequestId, candidate))
                .sorted(Comparator
                        .comparingInt((ScoredCandidate candidate) -> candidate.scoreSnapshot().pairScore()).reversed()
                        .thenComparingInt(candidate -> candidate.candidate().distanceMeters())
                        .thenComparing(candidate -> candidate.candidate().requestId()))
                .toList();

        for (ScoredCandidate rankedCandidate : rankedCandidates) {
            Optional<MatchProposal> proposal = reserveAndCreate(sourceRequestId, rankedCandidate);
            if (proposal.isPresent()) {
                return proposal;
            }
        }
        return Optional.empty();
    }

    private ScoredCandidate scoreCandidate(Long sourceRequestId, BidirectionalMatchCandidate candidate) {
        MatchRequest source = matchRequestRepository.findDetailedById(sourceRequestId)
                .orElseThrow(() -> new IllegalStateException("후보 탐색 요청을 찾을 수 없습니다."));
        MatchRequest target = matchRequestRepository.findDetailedById(candidate.requestId())
                .orElseThrow(() -> new IllegalStateException("후보 요청을 찾을 수 없습니다."));

        UserPersonalityProfile sourceProfile = personalityProfileRepository
                .findByUserId(source.getUser().getId())
                .orElse(null);
        UserPersonalityProfile targetProfile = personalityProfileRepository
                .findByUserId(target.getUser().getId())
                .orElse(null);
        UserPersonalityEmbedding sourceEmbedding = personalityEmbeddingRepository
                .findById(source.getUser().getId())
                .orElse(null);
        UserPersonalityEmbedding targetEmbedding = personalityEmbeddingRepository
                .findById(target.getUser().getId())
                .orElse(null);

        DirectionScore sourceToTarget = calculateDirection(
                source, target, sourceProfile, targetProfile, targetEmbedding
        );
        DirectionScore targetToSource = calculateDirection(
                target, source, targetProfile, sourceProfile, sourceEmbedding
        );
        short pairScore = (short) Math.min(sourceToTarget.score(), targetToSource.score());

        return new ScoredCandidate(candidate, new BidirectionalMatchScoreSnapshot(
                sourceToTarget.score(), sourceToTarget.reasons(),
                targetToSource.score(), targetToSource.reasons(),
                pairScore, FORMULA_VERSION
        ));
    }

    private DirectionScore calculateDirection(
            MatchRequest requester,
            MatchRequest candidate,
            UserPersonalityProfile requesterProfile,
            UserPersonalityProfile candidateProfile,
            UserPersonalityEmbedding candidateEmbedding
    ) {
        PersonalityCompatibilityScore compatibility = personalityCompatibilityCalculator.calculate(
                toScores(requesterProfile),
                toScores(candidateProfile),
                preferencesOf(requester),
                requester.getDesiredPersonalityTags(),
                candidateProfile == null ? null : candidateProfile.getStyleTags(),
                requestedEmbeddingOf(requester),
                toEmbedding(candidateEmbedding)
        );
        if (!compatibility.available()) {
            return new DirectionScore(BASE_CONDITION_SCORE, List.of(FALLBACK_REASON));
        }

        List<String> reasons = compatibility.matchedTags().isEmpty()
                ? List.of("선택한 성향 선호를 반영했어요.")
                : List.of("원하는 성향 태그와 잘 맞아요.");
        return new DirectionScore(compatibility.score(), reasons);
    }

    private Optional<MatchProposal> reserveAndCreate(Long sourceRequestId, ScoredCandidate candidate) {
        List<Long> requestIds = List.of(sourceRequestId, candidate.candidate().requestId()).stream()
                .sorted()
                .toList();
        List<MatchRequest> lockedRequests = matchRequestRepository.findAllByIdInForUpdate(requestIds);
        if (lockedRequests.size() != 2) {
            return Optional.empty();
        }

        MatchRequest source = lockedRequests.stream()
                .filter(request -> request.getId().equals(sourceRequestId))
                .findFirst().orElseThrow();
        MatchRequest target = lockedRequests.stream()
                .filter(request -> request.getId().equals(candidate.candidate().requestId()))
                .findFirst().orElseThrow();
        if (!candidateSearchService.isMutuallyEligible(source, target)) {
            return Optional.empty();
        }

        long request1Id = requestIds.get(0);
        long request2Id = requestIds.get(1);
        if (matchProposalRepository.existsByRequest1IdAndRequest2Id(request1Id, request2Id)) {
            return Optional.empty();
        }

        source.startConfirming();
        target.startConfirming();
        MatchProposal proposal = MatchProposal.of(
                source,
                target,
                candidate.scoreSnapshot(),
                Instant.now().plus(PROPOSAL_TTL)
        );
        return Optional.of(matchProposalRepository.save(proposal));
    }

    private PersonalityScoresResponse toScores(UserPersonalityProfile profile) {
        if (profile == null) {
            return null;
        }
        return new PersonalityScoresResponse(
                profile.getConversationLevel(), profile.getMealPace(),
                profile.getPlanningStyle(), profile.getNoveltyPreference()
        );
    }

    private Map<PersonalityDimension, DimensionMatchPreference> preferencesOf(MatchRequest request) {
        MatchingPreferenceSnapshot preferenceSnapshot = request.getPreferenceSnapshot();
        return preferenceSnapshot == null ? null : preferenceSnapshot.dimensions();
    }

    private PersonalityEmbeddingVector requestedEmbeddingOf(MatchRequest request) {
        return new PersonalityEmbeddingVector(
                request.getDesiredPersonalityEmbedding(), request.getEmbeddingModel(), request.getEmbeddingVersion()
        );
    }

    private PersonalityEmbeddingVector toEmbedding(UserPersonalityEmbedding embedding) {
        if (embedding == null) {
            return null;
        }
        return new PersonalityEmbeddingVector(
                embedding.getEmbedding(), embedding.getModelName(), embedding.getSourceVersion()
        );
    }

    private record DirectionScore(short score, List<String> reasons) {
    }

    private record ScoredCandidate(
            BidirectionalMatchCandidate candidate,
            BidirectionalMatchScoreSnapshot scoreSnapshot
    ) {
    }
}
