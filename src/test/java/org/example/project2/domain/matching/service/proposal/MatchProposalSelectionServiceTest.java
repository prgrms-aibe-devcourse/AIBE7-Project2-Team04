package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.matching.service.candidate.BidirectionalCandidateSearchService;
import org.example.project2.domain.matching.service.request.RealtimeMatchRedisLifecycleService;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchProposalSelectionServiceTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Mock BidirectionalCandidateSearchService candidateSearchService;
    @Mock MatchRequestRepository matchRequestRepository;
    @Mock MatchProposalRepository matchProposalRepository;
    @Mock UserPersonalityProfileRepository personalityProfileRepository;
    @Mock UserPersonalityEmbeddingRepository personalityEmbeddingRepository;
    @Mock PersonalityCompatibilityCalculator personalityCompatibilityCalculator;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RealtimeMatchRedisLifecycleService redisLifecycleService;
    @Mock ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks MatchProposalSelectionService service;

    @Test
    void createsProposalWithTheLowerOfBothDirectionalScores() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest candidate = request(2L, user("candidate"));
        prepareCandidate(source, candidate);
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score((short) 82), score((short) 68));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchProposal proposal = service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        assertThat(source.getStatus()).isEqualTo(MatchRequestStatus.CONFIRMING);
        assertThat(candidate.getStatus()).isEqualTo(MatchRequestStatus.CONFIRMING);
        assertThat(proposal.getScoreSnapshot().sourceToTargetScore()).isEqualTo((short) 82);
        assertThat(proposal.getScoreSnapshot().targetToSourceScore()).isEqualTo((short) 68);
        assertThat(proposal.getScoreSnapshot().pairScore()).isEqualTo((short) 68);
        assertThat(proposal.getScoreSnapshot().sourceToTargetMatchedTags()).isEmpty();
        assertThat(proposal.getScoreSnapshot().formulaVersion())
                .isEqualTo(MatchProposalSelectionService.FORMULA_VERSION);
        verify(redisLifecycleService).suspendWaitingForProposalAfterCommit(proposal);
        verify(redisLifecycleService).putProposalAfterCommit(proposal);
        verify(personalityCompatibilityCalculator, times(2))
                .calculate(any(), any(), any(), any());
    }

    @Test
    void passesEachRequestFreeTextVectorToTheOppositeUsersSelfDescriptionVector() {
        MatchRequest source = request(1L, user("source"), "조용한 식사를 선호해요.");
        MatchRequest candidate = request(2L, user("candidate"), "새로운 음식을 함께 즐겨요.");
        float[] sourceDesiredValues = embeddingValues(1, 0);
        float[] candidateDesiredValues = embeddingValues(0, 1);
        source.updateDesiredPersonalityEmbedding(
                sourceDesiredValues,
                "embedding-model",
                "PERSONALITY_FREE_TEXT_V2",
                Instant.now()
        );
        candidate.updateDesiredPersonalityEmbedding(
                candidateDesiredValues,
                "embedding-model",
                "PERSONALITY_FREE_TEXT_V2",
                Instant.now()
        );

        UserPersonalityProfile sourceProfile = profile(
                source.getUser(),
                Set.of(PersonalityTag.GOOD_LISTENER),
                "천천히 대화하는 편이에요."
        );
        UserPersonalityProfile candidateProfile = profile(
                candidate.getUser(),
                Set.of(PersonalityTag.FOOD_TALK),
                "새로운 메뉴를 이야기하는 걸 좋아해요."
        );
        UserPersonalityEmbedding sourceEmbedding = embeddingEntity(
                sourceProfile,
                source.getUser().getId(),
                sourceProfile.getSelfDescription(),
                embeddingValues(0, 1)
        );
        UserPersonalityEmbedding candidateEmbedding = embeddingEntity(
                candidateProfile,
                candidate.getUser().getId(),
                candidateProfile.getSelfDescription(),
                embeddingValues(1, 0)
        );

        prepareCandidate(source, candidate);
        when(personalityProfileRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(sourceProfile, candidateProfile));
        when(personalityEmbeddingRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(sourceEmbedding, candidateEmbedding));
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score((short) 82), score((short) 68));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        ArgumentCaptor<PersonalityEmbeddingVector> desiredVectorCaptor =
                ArgumentCaptor.forClass(PersonalityEmbeddingVector.class);
        ArgumentCaptor<PersonalityEmbeddingVector> candidateVectorCaptor =
                ArgumentCaptor.forClass(PersonalityEmbeddingVector.class);
        verify(personalityCompatibilityCalculator, times(2)).calculate(
                any(), any(), desiredVectorCaptor.capture(), candidateVectorCaptor.capture()
        );

        List<PersonalityEmbeddingVector> desiredVectors = desiredVectorCaptor.getAllValues();
        List<PersonalityEmbeddingVector> candidateVectors = candidateVectorCaptor.getAllValues();
        assertThat(desiredVectors).hasSize(2);
        assertThat(candidateVectors).hasSize(2);

        assertThat(desiredVectors.get(0).values()).containsExactly(sourceDesiredValues);
        assertThat(desiredVectors.get(0).modelName()).isEqualTo("embedding-model");
        assertThat(desiredVectors.get(0).sourceVersion()).isEqualTo("PERSONALITY_FREE_TEXT_V2");
        assertThat(candidateVectors.get(0).values())
                .containsExactly(candidateEmbedding.getEmbedding());
        assertThat(candidateVectors.get(0).modelName()).isEqualTo("embedding-model");
        assertThat(candidateVectors.get(0).sourceVersion()).isEqualTo("PERSONALITY_FREE_TEXT_V2");

        assertThat(desiredVectors.get(1).values()).containsExactly(candidateDesiredValues);
        assertThat(candidateVectors.get(1).values())
                .containsExactly(sourceEmbedding.getEmbedding());
        verify(matchRequestRepository, times(1)).findAllDetailedByIdIn(anyList());
        verify(matchRequestRepository, never()).findDetailedById(any());
        verify(personalityProfileRepository, times(1)).findAllByUserIdIn(anyList());
        verify(personalityEmbeddingRepository, times(1)).findAllByUserIdIn(anyList());
        verify(personalityProfileRepository, never()).findByUserId(any());
        verify(personalityEmbeddingRepository, never()).findById(any());
    }

    @Test
    void passesDesiredAndCandidateTagsToTheCalculatorAndReturnsTopMatchedTags() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest candidate = request(2L, user("candidate"));
        UserPersonalityProfile sourceProfile = profile(
                source.getUser(),
                Set.of(PersonalityTag.GOOD_LISTENER),
                "source"
        );
        UserPersonalityProfile candidateProfile = profile(
                candidate.getUser(),
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT,
                        PersonalityTag.DEEP_TALK
                ),
                "candidate"
        );
        prepareCandidate(source, candidate);
        when(personalityProfileRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(sourceProfile, candidateProfile));
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score(
                        (short) 82,
                        Set.of(
                                PersonalityTag.GOOD_LISTENER,
                                PersonalityTag.FOOD_TALK,
                                PersonalityTag.ENJOY_DESSERT,
                                PersonalityTag.DEEP_TALK
                        )
                ), score((short) 68, Set.of(PersonalityTag.GOOD_LISTENER)));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchProposal proposal = service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        assertThat(proposal.getScoreSnapshot().sourceToTargetMatchedTags())
                .containsExactly(
                        PersonalityTag.DEEP_TALK,
                        PersonalityTag.ENJOY_DESSERT,
                        PersonalityTag.FOOD_TALK
                );
        assertThat(proposal.getScoreSnapshot().targetToSourceMatchedTags())
                .containsExactly(PersonalityTag.GOOD_LISTENER);
        verify(personalityProfileRepository, times(1)).findAllByUserIdIn(anyList());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MatchProposalCreatedEvent event = (MatchProposalCreatedEvent) eventCaptor.getValue();
        assertThat(event.request1Payload().matchedTags())
                .containsExactly(
                        PersonalityTag.DEEP_TALK,
                        PersonalityTag.ENJOY_DESSERT,
                        PersonalityTag.FOOD_TALK
                );
    }

    @Test
    void doesNotUseProfileEmbeddingAfterAiConsentIsWithdrawn() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest candidate = request(2L, user("candidate"));
        UserPersonalityProfile withdrawnProfile = profile(
                candidate.getUser(),
                Set.of(PersonalityTag.GOOD_LISTENER),
                null,
                false
        );

        prepareCandidate(source, candidate);
        UserPersonalityEmbedding withdrawnEmbedding = embeddingEntity(
                withdrawnProfile,
                candidate.getUser().getId(),
                "withdrawn",
                embeddingValues(1, 0)
        );
        when(personalityProfileRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(withdrawnProfile));
        when(personalityEmbeddingRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(withdrawnEmbedding));
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score((short) 82), score((short) 68));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        ArgumentCaptor<PersonalityEmbeddingVector> candidateVectorCaptor =
                ArgumentCaptor.forClass(PersonalityEmbeddingVector.class);
        verify(personalityCompatibilityCalculator, times(2)).calculate(
                any(), any(), any(), candidateVectorCaptor.capture()
        );
        assertThat(candidateVectorCaptor.getAllValues()).containsOnlyNulls();
    }

    @Test
    void publishesProfileConfirmationEventWithOneDeadlineForBothUsers() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest candidate = request(2L, user("candidate"));
        prepareCandidate(source, candidate);
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score((short) 82), score((short) 68));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchProposal proposal = service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(MatchProposalCreatedEvent.class);
        MatchProposalCreatedEvent event = (MatchProposalCreatedEvent) eventCaptor.getValue();
        assertThat(event.request1UserId()).isEqualTo(proposal.getRequest1().getUser().getId());
        assertThat(event.request2UserId()).isEqualTo(proposal.getRequest2().getUser().getId());
        assertThat(event.request1Payload().expiresAt()).isEqualTo(event.request2Payload().expiresAt());
        assertThat(event.request1Payload().expiresAt()).isEqualTo(proposal.getExpiresAt());
    }

    @Test
    void keepsEligibleRequestsAndUsesBaseScoreWhenPersonalityDataIsUnavailable() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest candidate = request(2L, user("candidate"));
        prepareCandidate(source, candidate);
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(PersonalityCompatibilityScore.unavailable("DESIRED_PERSONALITY_MATCH_V1"));
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchProposal proposal = service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        assertThat(proposal.getScoreSnapshot().sourceToTargetScore()).isEqualTo((short) 50);
        assertThat(proposal.getScoreSnapshot().targetToSourceScore()).isEqualTo((short) 50);
        assertThat(proposal.getScoreSnapshot().pairScore()).isEqualTo((short) 50);
        assertThat(proposal.getScoreSnapshot().sourceToTargetReasons()).isNotEmpty();
    }

    @Test
    void leavesRequestWaitingWhenNoCandidateExists() {
        MatchRequest source = request(1L, user("source"));
        when(candidateSearchService.findCandidates(source.getUser().getId(), source.getId()))
                .thenReturn(List.of());

        Optional<MatchProposal> result = service.selectAndCreate(source.getUser().getId(), source.getId());

        assertThat(result).isEmpty();
        assertThat(source.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        verify(matchProposalRepository, never()).save(any());
        verify(personalityCompatibilityCalculator, never()).calculate(any(), any(), any(), any());
    }

    @Test
    void ordersEqualScoreCandidatesByDistanceThenWaitingStart() {
        MatchRequest source = request(1L, user("source"));
        MatchRequest laterCandidate = request(2L, user("later"));
        MatchRequest earlierCandidate = request(3L, user("earlier"));
        Instant earlier = Instant.parse("2026-08-27T09:00:00Z");
        Instant later = Instant.parse("2026-08-27T09:05:00Z");

        when(candidateSearchService.findCandidates(source.getUser().getId(), source.getId()))
                .thenReturn(List.of(
                        new BidirectionalMatchCandidate(
                                laterCandidate.getId(), laterCandidate.getUser().getId(), 500, later
                        ),
                        new BidirectionalMatchCandidate(
                                earlierCandidate.getId(), earlierCandidate.getUser().getId(), 500, earlier
                        )
                ));
        when(matchRequestRepository.findAllDetailedByIdIn(anyList()))
                .thenReturn(List.of(source, laterCandidate, earlierCandidate));
        when(personalityProfileRepository.findAllByUserIdIn(anyList())).thenReturn(List.of());
        when(personalityEmbeddingRepository.findAllByUserIdIn(anyList())).thenReturn(List.of());
        when(personalityCompatibilityCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(score((short) 70), score((short) 70), score((short) 70), score((short) 70));
        when(matchRequestRepository.findAllByIdInForUpdate(List.of(source.getId(), earlierCandidate.getId())))
                .thenReturn(List.of(source, earlierCandidate));
        when(candidateSearchService.isMutuallyEligible(source, earlierCandidate)).thenReturn(true);
        when(matchProposalRepository.save(any(MatchProposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchProposal proposal = service.selectAndCreate(source.getUser().getId(), source.getId()).orElseThrow();

        assertThat(proposal.getOtherRequest(source.getId()).getId()).isEqualTo(earlierCandidate.getId());
        assertThat(laterCandidate.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
    }

    private void prepareCandidate(MatchRequest source, MatchRequest candidate) {
        BidirectionalMatchCandidate candidateInfo = new BidirectionalMatchCandidate(
                candidate.getId(), candidate.getUser().getId(), 500
        );
        when(candidateSearchService.findCandidates(source.getUser().getId(), source.getId()))
                .thenReturn(List.of(candidateInfo));
        when(matchRequestRepository.findAllDetailedByIdIn(anyList()))
                .thenReturn(List.of(source, candidate));
        when(matchRequestRepository.findAllByIdInForUpdate(List.of(source.getId(), candidate.getId())))
                .thenReturn(List.of(source, candidate));
        when(candidateSearchService.isMutuallyEligible(source, candidate)).thenReturn(true);
        when(personalityProfileRepository.findAllByUserIdIn(anyList())).thenReturn(List.of());
        when(personalityEmbeddingRepository.findAllByUserIdIn(anyList())).thenReturn(List.of());
    }

    private PersonalityCompatibilityScore score(short value) {
        return score(value, Set.of());
    }

    private PersonalityCompatibilityScore score(short value, Set<PersonalityTag> matchedTags) {
        return new PersonalityCompatibilityScore(
                true, value, value, null, matchedTags, "DESIRED_PERSONALITY_MATCH_V1"
        );
    }

    private MatchRequest request(Long id, User user) {
        return request(id, user, null);
    }

    private MatchRequest request(Long id, User user, String desiredPersonalityText) {
        MatchRequest request = MatchRequest.create(
                user,
                "KOREAN",
                Instant.parse("2026-08-27T10:00:00Z"),
                "11680",
                "서울특별시 강남구",
                "테스트 장소",
                point(127.000, 37.500),
                3_000,
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                desiredPersonalityText,
                "DESIRED_PERSONALITY_MATCH_V1"
        );
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private UserPersonalityProfile profile(User user, Set<PersonalityTag> styleTags, String selfDescription) {
        return profile(user, styleTags, selfDescription, true);
    }

    private UserPersonalityProfile profile(
            User user,
            Set<PersonalityTag> styleTags,
            String selfDescription,
            boolean aiAnalysisConsent
    ) {
        Instant now = Instant.now();
        return UserPersonalityProfile.builder()
                .userId(user.getId())
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 50)
                .mealPace((short) 50)
                .planningStyle((short) 50)
                .noveltyPreference((short) 50)
                .styleTags(styleTags)
                .selfDescription(aiAnalysisConsent ? selfDescription : null)
                .aiAnalysisConsent(aiAnalysisConsent)
                .completedAt(now)
                .updatedAt(now)
                .build();
    }

    private UserPersonalityEmbedding embeddingEntity(
            UserPersonalityProfile profile,
            UUID userId,
            String sourceText,
            float[] embedding
    ) {
        return UserPersonalityEmbedding.builder()
                .userId(userId)
                .profile(profile)
                .sourceText(sourceText)
                .embedding(embedding)
                .modelName("embedding-model")
                .sourceVersion("PERSONALITY_FREE_TEXT_V2")
                .generatedAt(Instant.now())
                .build();
    }

    private float[] embeddingValues(float first, float second) {
        float[] values = new float[1536];
        values[0] = first;
        values[1] = second;
        return values;
    }

    private User user(String label) {
        String suffix = label + "-" + UUID.randomUUID();
        return User.builder()
                .id(UUID.randomUUID())
                .email(suffix + "@test.com")
                .passwordHash("hashed")
                .nickname(suffix)
                .build();
    }

    private org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        org.locationtech.jts.geom.Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
