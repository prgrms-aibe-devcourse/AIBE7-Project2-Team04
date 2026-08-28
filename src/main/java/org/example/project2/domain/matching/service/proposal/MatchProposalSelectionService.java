package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.matching.service.candidate.BidirectionalCandidateSearchService;
import org.example.project2.domain.matching.service.request.RealtimeMatchRedisLifecycleService;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchProposalSelectionService {
    public static final String FORMULA_VERSION = "DESIRED_PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1";

    private static final short BASE_CONDITION_SCORE = 50;
    private static final Duration PROPOSAL_TTL = Duration.ofSeconds(15);
    private static final String FALLBACK_REASON = "성향 정보가 부족해 기본 조건을 기준으로 제안했어요.";

    private final BidirectionalCandidateSearchService candidateSearchService;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final UserPersonalityProfileRepository personalityProfileRepository;
    private final UserPersonalityEmbeddingRepository personalityEmbeddingRepository;
    private final PersonalityCompatibilityCalculator personalityCompatibilityCalculator;
    private final ApplicationEventPublisher eventPublisher;
    private final RealtimeMatchRedisLifecycleService redisLifecycleService;

    @Transactional
    public Optional<MatchProposal> selectAndCreate(UUID userId, Long sourceRequestId) {
        List<BidirectionalMatchCandidate> hardFilteredCandidates =
                candidateSearchService.findCandidates(userId, sourceRequestId);
        if (hardFilteredCandidates.isEmpty()) {
            return Optional.empty();
        }

        MatchRequest source = matchRequestRepository.findDetailedById(sourceRequestId)
                .orElseThrow(() -> new IllegalStateException("후보 탐색 요청을 찾을 수 없습니다."));
        List<UUID> userIds = collectUserIds(source, hardFilteredCandidates);
        Map<UUID, UserPersonalityProfile> profilesByUserId = loadProfilesByUserId(userIds);
        Map<UUID, UserPersonalityEmbedding> embeddingsByUserId = loadEmbeddingsByUserId(userIds);

        List<ScoredCandidate> rankedCandidates = hardFilteredCandidates
                .stream()
                .map(candidate -> assembleRankingInput(
                        source,
                        candidate,
                        profilesByUserId,
                        embeddingsByUserId
                ))
                .map(this::scoreCandidate)
                .sorted(Comparator
                        // 성향 계산이 가능한 후보를 먼저 정렬하고, 계산 불가 후보는 기본 조건 순서로 fallback한다.
                        .comparingInt((ScoredCandidate candidate) ->
                                candidate.personalityScoreAvailable() ? 1 : 0).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (ScoredCandidate candidate) -> candidate.scoreSnapshot().pairScore()
                        ).reversed())
                        .thenComparingInt(candidate -> candidate.candidate().distanceMeters())
                        .thenComparing(
                                candidate -> candidate.candidate().waitingStartedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                candidate -> candidate.candidate().requestId(),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .toList();

        for (ScoredCandidate rankedCandidate : rankedCandidates) {
            Optional<MatchProposal> proposal = reserveAndCreate(
                    sourceRequestId,
                    rankedCandidate,
                    profilesByUserId
            );
            if (proposal.isPresent()) {
                return proposal;
            }
        }
        return Optional.empty();
    }

    /**
     * 하드 필터 결과와 성향 랭킹 입력을 분리하여, 하드 필터를 통과한 후보에게만
     * 성향 계산기를 호출하도록 호출 순서를 코드 구조로 강제한다.
     */
    private PersonalityRankingInput assembleRankingInput(
            MatchRequest source,
            BidirectionalMatchCandidate candidate,
            Map<UUID, UserPersonalityProfile> profilesByUserId,
            Map<UUID, UserPersonalityEmbedding> embeddingsByUserId
    ) {
        MatchRequest target = matchRequestRepository.findDetailedById(candidate.requestId())
                .orElseThrow(() -> new IllegalStateException("후보 요청을 찾을 수 없습니다."));

        UserPersonalityProfile sourceProfile = profilesByUserId.get(source.getUser().getId());
        UserPersonalityProfile targetProfile = profilesByUserId.get(target.getUser().getId());
        UserPersonalityEmbedding sourceSelfDescriptionEmbedding =
                embeddingsByUserId.get(source.getUser().getId());
        UserPersonalityEmbedding targetSelfDescriptionEmbedding =
                embeddingsByUserId.get(target.getUser().getId());
        sourceSelfDescriptionEmbedding = eligibleSelfDescriptionEmbedding(
                sourceProfile,
                sourceSelfDescriptionEmbedding
        );
        targetSelfDescriptionEmbedding = eligibleSelfDescriptionEmbedding(
                targetProfile,
                targetSelfDescriptionEmbedding
        );

        return new PersonalityRankingInput(
                candidate,
                source,
                target,
                sourceProfile,
                targetProfile,
                sourceSelfDescriptionEmbedding,
                targetSelfDescriptionEmbedding
        );
    }

    private ScoredCandidate scoreCandidate(PersonalityRankingInput input) {
        MatchRequest source = input.source();
        MatchRequest target = input.target();

        DirectionScore sourceToTarget = calculateDirection(
                source,
                input.targetProfile(),
                input.targetSelfDescriptionEmbedding()
        );
        DirectionScore targetToSource = calculateDirection(
                target,
                input.sourceProfile(),
                input.sourceSelfDescriptionEmbedding()
        );
        short pairScore = (short) Math.min(sourceToTarget.score(), targetToSource.score());

        return new ScoredCandidate(
                input.hardFilteredCandidate(),
                new BidirectionalMatchScoreSnapshot(
                        sourceToTarget.score(),
                        sourceToTarget.reasons(),
                        sourceToTarget.matchedTags(),
                        targetToSource.score(),
                        targetToSource.reasons(),
                        targetToSource.matchedTags(),
                        pairScore,
                        FORMULA_VERSION
                ),
                sourceToTarget.personalityScoreAvailable()
                        || targetToSource.personalityScoreAvailable()
        );
    }

    private DirectionScore calculateDirection(
            MatchRequest requester,
            UserPersonalityProfile candidateProfile,
            UserPersonalityEmbedding candidateSelfDescriptionEmbedding
    ) {
        PersonalityCompatibilityScore compatibility = personalityCompatibilityCalculator.calculate(
                requester.getDesiredPersonalityTags(),
                candidateProfile == null ? null : candidateProfile.getStyleTags(),
                requestedFreeTextEmbeddingOf(requester),
                selfDescriptionEmbeddingOf(candidateSelfDescriptionEmbedding)
        );
        if (!compatibility.available()) {
            return new DirectionScore(BASE_CONDITION_SCORE, List.of(), List.of(FALLBACK_REASON), false);
        }

        List<PersonalityTag> matchedTags = topMatchedTags(compatibility.matchedTags());
        List<String> reasons = matchedTags.isEmpty()
                ? compatibility.embeddingScore() == null
                ? List.of("선택한 성향 선호를 반영했어요.")
                : List.of("자유 서술한 식사 스타일이 비슷해요.")
                : List.of("원하는 성향 태그와 잘 맞아요.");
        return new DirectionScore(compatibility.score(), matchedTags, reasons, true);
    }

    private Optional<MatchProposal> reserveAndCreate(
            Long sourceRequestId,
            ScoredCandidate candidate,
            Map<UUID, UserPersonalityProfile> profilesByUserId
    ) {
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
        MatchProposal saved = matchProposalRepository.save(proposal);
        redisLifecycleService.suspendWaitingForProposalAfterCommit(saved);
        redisLifecycleService.putProposalAfterCommit(saved);
        publishCreatedEvent(saved, profilesByUserId);
        return Optional.of(saved);
    }

    private void publishCreatedEvent(
            MatchProposal proposal,
            Map<UUID, UserPersonalityProfile> profilesByUserId
    ) {
        MatchRequest request1 = proposal.getRequest1();
        MatchRequest request2 = proposal.getRequest2();
        eventPublisher.publishEvent(new MatchProposalCreatedEvent(
                request1.getUser().getId(),
                toResponse(proposal, request1, profilesByUserId),
                request2.getUser().getId(),
                toResponse(proposal, request2, profilesByUserId)
        ));
    }

    private MatchProposalResponse toResponse(
            MatchProposal proposal,
            MatchRequest viewerRequest,
            Map<UUID, UserPersonalityProfile> profilesByUserId
    ) {
        MatchRequest partnerRequest = proposal.getOtherRequest(viewerRequest.getId());
        if (partnerRequest == null || partnerRequest.getUser() == null) {
            throw new IllegalStateException("매칭 제안의 상대 사용자 정보를 찾을 수 없습니다.");
        }
        var partner = partnerRequest.getUser();
        Set<PersonalityTag> publicTags = Optional.ofNullable(profilesByUserId.get(partner.getId()))
                .map(UserPersonalityProfile::getStyleTags)
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
                MatchProposalDecision.PENDING,
                new MatchProposalPartnerProfileResponse(
                        partner.getId(),
                        partner.getNickname(),
                        partner.getProfileImageUrl(),
                        partner.getDescription(),
                        publicTags
                ),
                score,
                matchedTags,
                reasons
        );
    }

    private List<UUID> collectUserIds(
            MatchRequest source,
            List<BidirectionalMatchCandidate> candidates
    ) {
        Set<UUID> userIds = new LinkedHashSet<>();
        if (source.getUser() != null && source.getUser().getId() != null) {
            userIds.add(source.getUser().getId());
        }
        candidates.stream()
                .map(BidirectionalMatchCandidate::userId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        return List.copyOf(userIds);
    }

    private Map<UUID, UserPersonalityProfile> loadProfilesByUserId(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserPersonalityProfile> profilesByUserId = new LinkedHashMap<>();
        personalityProfileRepository.findAllByUserIdIn(userIds).forEach(profile -> {
            if (profile != null && profile.getUserId() != null) {
                profilesByUserId.putIfAbsent(profile.getUserId(), profile);
            }
        });
        return Map.copyOf(profilesByUserId);
    }

    private Map<UUID, UserPersonalityEmbedding> loadEmbeddingsByUserId(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserPersonalityEmbedding> embeddingsByUserId = new LinkedHashMap<>();
        personalityEmbeddingRepository.findAllByUserIdIn(userIds).forEach(embedding -> {
            if (embedding != null && embedding.getUserId() != null) {
                embeddingsByUserId.putIfAbsent(embedding.getUserId(), embedding);
            }
        });
        return Map.copyOf(embeddingsByUserId);
    }

    private PersonalityEmbeddingVector requestedFreeTextEmbeddingOf(MatchRequest request) {
        if (request.getDesiredPersonalityText() == null
                || request.getDesiredPersonalityText().isBlank()) {
            return null;
        }
        PersonalityEmbeddingVector vector = new PersonalityEmbeddingVector(
                request.getDesiredPersonalityEmbedding(),
                request.getEmbeddingModel(),
                request.getEmbeddingVersion()
        );
        return vector.isValidForCurrentRanking() ? vector : null;
    }

    private PersonalityEmbeddingVector selfDescriptionEmbeddingOf(UserPersonalityEmbedding embedding) {
        if (embedding == null) {
            return null;
        }
        PersonalityEmbeddingVector vector = new PersonalityEmbeddingVector(
                embedding.getEmbedding(),
                embedding.getModelName(),
                embedding.getSourceVersion()
        );
        return vector.isValidForCurrentRanking() ? vector : null;
    }

    private UserPersonalityEmbedding eligibleSelfDescriptionEmbedding(
            UserPersonalityProfile profile,
            UserPersonalityEmbedding embedding
    ) {
        if (profile == null || !profile.isAiAnalysisConsent()
                || profile.getSelfDescription() == null
                || profile.getSelfDescription().isBlank()
                || embedding == null
                || embedding.getSourceText() == null
                || embedding.getSourceText().isBlank()) {
            return null;
        }
        if (!Objects.equals(normalize(profile.getSelfDescription()), normalize(embedding.getSourceText()))) {
            return null;
        }
        return embedding;
    }

    private List<PersonalityTag> topMatchedTags(Set<PersonalityTag> matchedTags) {
        if (matchedTags == null || matchedTags.isEmpty()) {
            return List.of();
        }
        return matchedTags.stream()
                .sorted(Comparator.comparing(PersonalityTag::name))
                .limit(3)
                .toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record DirectionScore(
            short score,
            List<PersonalityTag> matchedTags,
            List<String> reasons,
            boolean personalityScoreAvailable
    ) {
    }

    private record PersonalityRankingInput(
            BidirectionalMatchCandidate hardFilteredCandidate,
            MatchRequest source,
            MatchRequest target,
            UserPersonalityProfile sourceProfile,
            UserPersonalityProfile targetProfile,
            UserPersonalityEmbedding sourceSelfDescriptionEmbedding,
            UserPersonalityEmbedding targetSelfDescriptionEmbedding
    ) {
    }

    private record ScoredCandidate(
            BidirectionalMatchCandidate candidate,
            BidirectionalMatchScoreSnapshot scoreSnapshot,
            boolean personalityScoreAvailable
    ) {
    }
}
