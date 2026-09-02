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
    private static final Duration PROPOSAL_TTL = Duration.ofSeconds(180);
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

        List<Long> requestIds = collectRequestIds(sourceRequestId, hardFilteredCandidates);
        Map<Long, MatchRequest> requestsById = loadRequestsById(requestIds);
        MatchRequest source = requestsById.get(sourceRequestId);
        if (source == null) {
            throw new IllegalStateException("후보 탐색 요청을 찾을 수 없습니다.");
        }
        List<UUID> userIds = collectUserIds(source, hardFilteredCandidates, requestsById);
        Map<UUID, UserPersonalityProfile> profilesByUserId = loadProfilesByUserId(userIds);
        Map<UUID, List<UserPersonalityEmbedding>> embeddingsByUserId = loadEmbeddingsByUserId(userIds);

        List<ScoredCandidate> rankedCandidates = hardFilteredCandidates
                .stream()
                .map(candidate -> assembleRankingInput(
                        source,
                        candidate,
                        requestsById,
                        profilesByUserId,
                        embeddingsByUserId
                ))
                .map(this::scoreCandidate)
                .sorted(Comparator
                        // 성향 계산이 가능한 후보를 먼저 정렬하고, 계산 불가 후보는 기본 조건 순서로 fallback한다.
                        .comparingInt((ScoredCandidate candidate) ->
                                candidate.personalityScoreAvailable() ? 1 : 0).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (ScoredCandidate candidate) -> candidate.scoreSnapshot().sourceToTargetScore()
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
            Map<Long, MatchRequest> requestsById,
            Map<UUID, UserPersonalityProfile> profilesByUserId,
            Map<UUID, List<UserPersonalityEmbedding>> embeddingsByUserId
    ) {
        MatchRequest target = requestsById.get(candidate.requestId());
        if (target == null) {
            throw new IllegalStateException("후보 요청을 찾을 수 없습니다.");
        }

        UserPersonalityProfile sourceProfile = profilesByUserId.get(source.getUser().getId());
        UserPersonalityProfile targetProfile = profilesByUserId.get(target.getUser().getId());
        List<UserPersonalityEmbedding> sourceSelfDescriptionEmbeddings =
                embeddingsByUserId.getOrDefault(source.getUser().getId(), List.of());
        List<UserPersonalityEmbedding> targetSelfDescriptionEmbeddings =
                embeddingsByUserId.getOrDefault(target.getUser().getId(), List.of());

        return new PersonalityRankingInput(
                candidate,
                source,
                target,
                sourceProfile,
                targetProfile,
                sourceSelfDescriptionEmbeddings,
                targetSelfDescriptionEmbeddings
        );
    }

    private ScoredCandidate scoreCandidate(PersonalityRankingInput input) {
        MatchRequest source = input.source();
        MatchRequest target = input.target();

        DirectionScore sourceToTarget = calculateDirection(
                source,
                target,
                input.targetProfile(),
                input.targetSelfDescriptionEmbeddings()
        );
        DirectionScore targetToSource = calculateDirection(
                target,
                source,
                input.sourceProfile(),
                input.sourceSelfDescriptionEmbeddings()
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
            MatchRequest candidateRequest,
            UserPersonalityProfile candidateProfile,
            List<UserPersonalityEmbedding> candidateEmbeddings
    ) {
        List<PersonalityEmbeddingVector> candidateVectors = selfDescriptionEmbeddingListOf(candidateProfile, candidateEmbeddings);
        PersonalityCompatibilityScore compatibility = personalityCompatibilityCalculator.calculateWithList(
                requester.getDesiredPersonalityTags(),
                candidateProfile == null ? null : candidateProfile.getStyleTags(),
                requestedFreeTextEmbeddingOf(requester),
                candidateVectors
        );
        if (!compatibility.available()) {
            return new DirectionScore(
                    BASE_CONDITION_SCORE,
                    List.of(),
                    buildBasicConditionReasons(requester, candidateRequest),
                    false
            );
        }

        List<PersonalityTag> matchedTags = topMatchedTags(compatibility.matchedTags());
        List<String> reasons = buildUserFacingReasons(requester, candidateRequest, matchedTags);

        return new DirectionScore(compatibility.score(), matchedTags, List.copyOf(reasons), true);
    }

    private List<String> buildUserFacingReasons(
            MatchRequest requester,
            MatchRequest candidateRequest,
            List<PersonalityTag> matchedTags
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        matchedTags.stream()
                .filter(Objects::nonNull)
                .limit(2)
                .map(this::personalityReason)
                .forEach(reasons::add);
        addConditionReasons(reasons, requester, candidateRequest);
        if (reasons.isEmpty()) {
            reasons.add(FALLBACK_REASON);
        }
        return reasons.stream().limit(3).toList();
    }

    private List<String> buildBasicConditionReasons(
            MatchRequest requester,
            MatchRequest candidateRequest
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        addConditionReasons(reasons, requester, candidateRequest);
        if (reasons.isEmpty()) {
            reasons.add(FALLBACK_REASON);
        }
        return reasons.stream().limit(3).toList();
    }

    private void addConditionReasons(
            LinkedHashSet<String> reasons,
            MatchRequest requester,
            MatchRequest candidateRequest
    ) {
        if (requester == null || candidateRequest == null) {
            return;
        }
        if (Objects.equals(requester.getFoodCategory(), candidateRequest.getFoodCategory())
                && requester.getFoodCategory() != null) {
            reasons.add("두 분 모두 " + foodCategoryLabel(requester.getFoodCategory())
                    + " 메뉴를 선호해 메뉴를 고르기 편해요.");
        }
        if (requester.getMealAt() != null && candidateRequest.getMealAt() != null
                && Duration.between(requester.getMealAt(), candidateRequest.getMealAt()).abs()
                .compareTo(Duration.ofMinutes(30)) <= 0) {
            reasons.add("원하는 식사 시간이 비슷해 약속을 잡기 편해요.");
        }
        if (Objects.equals(requester.getRegionCode(), candidateRequest.getRegionCode())) {
            reasons.add("선택한 만남 지역이 같아 이동하기 편해요.");
        }
    }

    private String personalityReason(PersonalityTag tag) {
        return switch (tag) {
            case INITIATES_CONVERSATION -> "상대방이 먼저 대화를 시작하는 편이라 어색함을 덜 수 있어요.";
            case GOOD_LISTENER -> "상대방이 이야기를 잘 들어줘 편안하게 대화할 수 있어요.";
            case FOOD_TALK -> "음식 이야기를 좋아하는 점이 잘 맞아요.";
            case LIGHT_CHAT -> "가볍고 편안한 대화를 함께 즐길 수 있어요.";
            case DEEP_TALK -> "생각과 취향을 깊게 나눌 수 있어요.";
            case COMFORTABLE_SILENCE -> "말없이 식사하는 시간도 편안하게 보낼 수 있어요.";
            case CALM_ATMOSPHERE -> "차분하고 여유로운 분위기를 함께 즐길 수 있어요.";
            case CHEERFUL_ATMOSPHERE -> "밝고 유쾌한 분위기를 함께 만들 수 있어요.";
            case ACTIVE_ATMOSPHERE -> "활기찬 분위기에서 즐겁게 식사할 수 있어요.";
            case SHARE_DISHES -> "여러 메뉴를 함께 나눠 먹는 즐거움이 잘 맞아요.";
            case TAKE_FOOD_PHOTOS -> "맛있는 순간을 사진으로 남기는 취향이 잘 맞아요.";
            case ENJOY_DESSERT -> "식사 후 디저트까지 함께 즐길 수 있어요.";
            case FOCUS_ON_MEAL -> "식사에 집중하며 여유롭게 시간을 보낼 수 있어요.";
        };
    }

    private String foodCategoryLabel(String foodCategory) {
        return switch (foodCategory) {
            case "KOREAN" -> "한식";
            case "JAPANESE" -> "일식";
            case "CHINESE" -> "중식";
            case "WESTERN" -> "양식";
            case "SOUTHEAST_ASIAN" -> "동남아 음식";
            case "SNACK" -> "분식";
            case "FAST_FOOD" -> "패스트푸드";
            case "CAFE_DESSERT" -> "카페·디저트";
            default -> "같은 음식";
        };
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
        Short myScore = snapshot == null
                ? null
                : viewerIsRequest1 ? snapshot.sourceToTargetScore() : snapshot.targetToSourceScore();
        Short partnerScore = snapshot == null
                ? null
                : viewerIsRequest1 ? snapshot.targetToSourceScore() : snapshot.sourceToTargetScore();
        Short score = myScore;

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
                myScore,
                partnerScore,
                matchedTags,
                reasons
        );
    }

    private List<Long> collectRequestIds(
            Long sourceRequestId,
            List<BidirectionalMatchCandidate> candidates
    ) {
        Set<Long> requestIds = new LinkedHashSet<>();
        if (sourceRequestId != null) {
            requestIds.add(sourceRequestId);
        }
        candidates.stream()
                .map(BidirectionalMatchCandidate::requestId)
                .filter(Objects::nonNull)
                .forEach(requestIds::add);
        return List.copyOf(requestIds);
    }

    private Map<Long, MatchRequest> loadRequestsById(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MatchRequest> requestsById = new LinkedHashMap<>();
        matchRequestRepository.findAllDetailedByIdIn(requestIds).forEach(request -> {
            if (request != null && request.getId() != null) {
                requestsById.putIfAbsent(request.getId(), request);
            }
        });
        return Map.copyOf(requestsById);
    }

    private List<UUID> collectUserIds(
            MatchRequest source,
            List<BidirectionalMatchCandidate> candidates,
            Map<Long, MatchRequest> requestsById
    ) {
        Set<UUID> userIds = new LinkedHashSet<>();
        if (source.getUser() != null && source.getUser().getId() != null) {
            userIds.add(source.getUser().getId());
        }
        candidates.stream()
                .map(BidirectionalMatchCandidate::requestId)
                .map(requestsById::get)
                .filter(Objects::nonNull)
                .map(MatchRequest::getUser)
                .filter(Objects::nonNull)
                .map(org.example.project2.domain.user.entity.User::getId)
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

    private Map<UUID, List<UserPersonalityEmbedding>> loadEmbeddingsByUserId(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UserPersonalityEmbedding>> embeddingsByUserId = new LinkedHashMap<>();
        personalityEmbeddingRepository.findAllByProfileUserIdIn(userIds).forEach(embedding -> {
            if (embedding != null && embedding.getProfile() != null && embedding.getProfile().getUserId() != null) {
                embeddingsByUserId.computeIfAbsent(embedding.getProfile().getUserId(), k -> new java.util.ArrayList<>())
                        .add(embedding);
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

    private List<PersonalityEmbeddingVector> selfDescriptionEmbeddingListOf(
            UserPersonalityProfile profile,
            List<UserPersonalityEmbedding> embeddings
    ) {
        if (profile == null || !profile.isAiAnalysisConsent() || profile.getSelfDescription() == null
                || profile.getSelfDescription().isBlank() || embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }
        List<PersonalityEmbeddingVector> vectors = new java.util.ArrayList<>();
        for (UserPersonalityEmbedding emb : embeddings) {
            if (emb != null && emb.getEmbedding() != null) {
                PersonalityEmbeddingVector vec = new PersonalityEmbeddingVector(
                        emb.getEmbedding(),
                        emb.getModelName(),
                        emb.getSourceVersion()
                );
                if (vec.isValidForCurrentRanking()) {
                    vectors.add(vec);
                }
            }
        }
        return List.copyOf(vectors);
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
            List<UserPersonalityEmbedding> sourceSelfDescriptionEmbeddings,
            List<UserPersonalityEmbedding> targetSelfDescriptionEmbeddings
    ) {
    }

    private record ScoredCandidate(
            BidirectionalMatchCandidate candidate,
            BidirectionalMatchScoreSnapshot scoreSnapshot,
            boolean personalityScoreAvailable
    ) {
    }
}
