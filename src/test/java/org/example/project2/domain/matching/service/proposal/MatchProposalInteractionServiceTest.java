package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionRequest;
import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionType;
import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.exception.proposal.MatchProposalErrorCode;
import org.example.project2.domain.matching.exception.proposal.MatchProposalException;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchProposalInteractionServiceTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Mock MatchProposalRepository matchProposalRepository;
    @Mock MatchProposalLifecycleService matchProposalLifecycleService;
    @Mock UserPersonalityProfileRepository profileRepository;
    @Mock ProfileImageUrlResolver profileImageUrlResolver;

    private MatchProposalInteractionService service;
    private User sourceUser;
    private User targetUser;
    private MatchProposal proposal;

    @BeforeEach
    void setUp() {
        service = new MatchProposalInteractionService(
                matchProposalRepository,
                matchProposalLifecycleService,
                profileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                profileImageUrlResolver
        );
        sourceUser = user("source", "소스 닉네임");
        targetUser = user("target", "상대 닉네임");
        MatchRequest source = request(1L, sourceUser);
        MatchRequest target = request(2L, targetUser);
        proposal = MatchProposal.of(
                source,
                target,
                new BidirectionalMatchScoreSnapshot(
                        (short) 81,
                        List.of("대화 선호가 잘 맞아요."),
                        (short) 74,
                        List.of("식사 취향을 반영했어요."),
                        (short) 74,
                        "DESIRED_PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1"
                ),
                NOW.plusSeconds(15)
        );
        ReflectionTestUtils.setField(proposal, "id", 10L);
    }

    @Test
    void returnsOnlyPublicPartnerProfileFieldsAndViewerDirectionReason() {
        UserPersonalityProfile publicProfile = org.mockito.Mockito.mock(UserPersonalityProfile.class);
        when(publicProfile.getStyleTags()).thenReturn(Set.of(PersonalityTag.GOOD_LISTENER));
        when(matchProposalRepository.findPendingByUserId(sourceUser.getId()))
                .thenReturn(Optional.of(proposal));
        when(profileRepository.findByUserId(targetUser.getId())).thenReturn(Optional.of(publicProfile));
        when(profileImageUrlResolver.resolve(targetUser))
                .thenReturn("https://cdn.example/profile.png");

        MatchProposalResponse response = service.getCurrent(sourceUser.getId());

        assertThat(response.proposalId()).isEqualTo(proposal.getId());
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(response.myDecision()).isEqualTo(MatchProposalDecision.PENDING);
        assertThat(response.partner().userId()).isEqualTo(targetUser.getId());
        assertThat(response.partner().nickname()).isEqualTo("상대 닉네임");
        assertThat(response.partner().profileImageUrl()).isEqualTo("https://cdn.example/profile.png");
        assertThat(response.partner().description()).isEqualTo("같이 편하게 식사하고 싶어요.");
        assertThat(response.partner().styleTags()).containsExactly(PersonalityTag.GOOD_LISTENER);
        assertThat(response.compatibilityScore()).isEqualTo((short) 74);
        assertThat(response.compatibilityReasons()).containsExactly("대화 선호가 잘 맞아요.");

        Set<String> partnerFields = Arrays.stream(MatchProposalPartnerProfileResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertThat(partnerFields).containsExactlyInAnyOrder(
                "userId", "nickname", "profileImageUrl", "description", "styleTags"
        );
        assertThat(partnerFields).doesNotContain(
                "email", "authProvider", "providerId", "location", "latitude", "longitude",
                "desiredPersonalityText", "selfDescription", "embedding"
        );
    }

    @Test
    void doesNotExposeOtherUsersAcceptanceBeforeFinalResult() {
        proposal.decide(proposal.getRequest1().getId(), MatchProposalDecision.ACCEPTED, NOW);
        when(matchProposalRepository.findPendingByUserId(targetUser.getId()))
                .thenReturn(Optional.of(proposal));
        when(profileRepository.findByUserId(sourceUser.getId())).thenReturn(Optional.empty());

        MatchProposalResponse response = service.getCurrent(targetUser.getId());

        assertThat(response.myDecision()).isEqualTo(MatchProposalDecision.PENDING);
        Set<String> responseFields = Arrays.stream(MatchProposalResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertThat(responseFields).doesNotContain(
                "partnerDecision", "request1Decision", "request2Decision",
                "partnerDecidedAt", "request1DecidedAt", "request2DecidedAt"
        );
    }

    @Test
    void blocksDecisionFromNonParticipant() {
        when(matchProposalRepository.findByIdForUpdate(proposal.getId())).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.decide(
                UUID.randomUUID(), proposal.getId(),
                new MatchProposalDecisionRequest(MatchProposalDecisionType.ACCEPT)
        )).isInstanceOfSatisfying(MatchProposalException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MatchProposalErrorCode.PROPOSAL_FORBIDDEN));

        verify(matchProposalLifecycleService, never()).decide(any(), any(), any(), any());
    }

    @Test
    void rejectsDecisionAfterProposalExpiry() {
        when(matchProposalRepository.findByIdForUpdate(proposal.getId())).thenReturn(Optional.of(proposal));
        MatchProposal expired = MatchProposal.of(
                proposal.getRequest1(), proposal.getRequest2(), proposal.getScoreSnapshot(), NOW.minusSeconds(1)
        );
        when(matchProposalRepository.findByIdForUpdate(proposal.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.decide(
                sourceUser.getId(), proposal.getId(),
                new MatchProposalDecisionRequest(MatchProposalDecisionType.ACCEPT)
        )).isInstanceOfSatisfying(MatchProposalException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MatchProposalErrorCode.PROPOSAL_STATE_CONFLICT));

        verify(matchProposalLifecycleService, never()).decide(any(), any(), any(), any());
    }

    @Test
    void delegatesDecisionAndReturnsUpdatedProposal() {
        when(matchProposalRepository.findByIdForUpdate(proposal.getId())).thenReturn(Optional.of(proposal));
        when(matchProposalLifecycleService.decide(
                proposal.getId(), proposal.getRequest1().getId(),
                MatchProposalDecision.ACCEPTED, NOW
        )).thenReturn(proposal);

        MatchProposalResponse response = service.decide(
                sourceUser.getId(), proposal.getId(),
                new MatchProposalDecisionRequest(MatchProposalDecisionType.ACCEPT)
        );

        assertThat(response.status()).isEqualTo(proposal.getStatus());
        verify(matchProposalLifecycleService).decide(
                proposal.getId(), proposal.getRequest1().getId(),
                MatchProposalDecision.ACCEPTED, NOW
        );
    }

    @Test
    void treatsRepeatedRejectionAsIdempotentAfterProposalIsClosed() {
        proposal.decide(proposal.getRequest1().getId(), MatchProposalDecision.REJECTED, NOW);
        when(matchProposalRepository.findByIdForUpdate(proposal.getId())).thenReturn(Optional.of(proposal));

        MatchProposalResponse response = service.decide(
                sourceUser.getId(), proposal.getId(),
                new MatchProposalDecisionRequest(MatchProposalDecisionType.REJECT)
        );

        assertThat(response.myDecision()).isEqualTo(MatchProposalDecision.REJECTED);
        assertThat(response.status()).isEqualTo(org.example.project2.domain.matching.entity.MatchProposalStatus.REJECTED);
        verify(matchProposalLifecycleService, never()).decide(any(), any(), any(), any());
    }

    private MatchRequest request(Long id, User user) {
        MatchRequest request = MatchRequest.create(
                user, "KOREAN", NOW.plusSeconds(3600), "11680", "서울특별시 강남구", "테스트 장소",
                point(127.000, 37.500), 3_000,
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK, PersonalityTag.ENJOY_DESSERT),
                null, "DESIRED_PERSONALITY_MATCH_V1"
        );
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private User user(String label, String nickname) {
        String suffix = label + "-" + UUID.randomUUID();
        return User.builder()
                .id(UUID.randomUUID())
                .email(suffix + "@test.com")
                .passwordHash("hashed")
                .nickname(nickname)
                .profileImageUrl("https://cdn.example/profile.png")
                .description("같이 편하게 식사하고 싶어요.")
                .build();
    }

    private org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        org.locationtech.jts.geom.Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
