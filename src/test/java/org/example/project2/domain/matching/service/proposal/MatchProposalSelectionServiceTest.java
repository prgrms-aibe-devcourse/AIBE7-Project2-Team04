package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.matching.service.candidate.BidirectionalCandidateSearchService;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
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
import static org.mockito.ArgumentMatchers.eq;
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
        assertThat(proposal.getScoreSnapshot().formulaVersion())
                .isEqualTo(MatchProposalSelectionService.FORMULA_VERSION);
        verify(personalityCompatibilityCalculator, times(2))
                .calculate(any(), any(), any(), any());
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
    }

    private void prepareCandidate(MatchRequest source, MatchRequest candidate) {
        BidirectionalMatchCandidate candidateInfo = new BidirectionalMatchCandidate(
                candidate.getId(), candidate.getUser().getId(), 500
        );
        when(candidateSearchService.findCandidates(source.getUser().getId(), source.getId()))
                .thenReturn(List.of(candidateInfo));
        when(matchRequestRepository.findDetailedById(source.getId())).thenReturn(Optional.of(source));
        when(matchRequestRepository.findDetailedById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(matchRequestRepository.findAllByIdInForUpdate(List.of(source.getId(), candidate.getId())))
                .thenReturn(List.of(source, candidate));
        when(candidateSearchService.isMutuallyEligible(source, candidate)).thenReturn(true);
        when(personalityProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(personalityEmbeddingRepository.findById(any())).thenReturn(Optional.empty());
    }

    private PersonalityCompatibilityScore score(short value) {
        return new PersonalityCompatibilityScore(
                true, value, value, null, Set.of(), "DESIRED_PERSONALITY_MATCH_V1"
        );
    }

    private MatchRequest request(Long id, User user) {
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
                null,
                "DESIRED_PERSONALITY_MATCH_V1"
        );
        ReflectionTestUtils.setField(request, "id", id);
        return request;
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
