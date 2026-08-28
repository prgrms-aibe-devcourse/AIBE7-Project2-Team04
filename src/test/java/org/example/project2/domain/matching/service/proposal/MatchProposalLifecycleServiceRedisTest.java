package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.request.RealtimeMatchRedisLifecycleService;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchProposalLifecycleServiceRedisTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock MatchProposalRepository proposalRepository;
    @Mock MatchRequestRepository requestRepository;
    @Mock MatchRepository matchRepository;
    @Mock MatchParticipantRepository participantRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock UserPersonalityProfileRepository profileRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RealtimeMatchRedisLifecycleService redisLifecycleService;

    private MatchRequest request1;
    private MatchRequest request2;
    private MatchProposal proposal;
    private MatchProposalLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new MatchProposalLifecycleService(
                proposalRepository,
                requestRepository,
                matchRepository,
                participantRepository,
                chatRoomRepository,
                profileRepository,
                eventPublisher,
                redisLifecycleService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        request1 = request(1L);
        request2 = request(2L);
        request1.startConfirming();
        request2.startConfirming();
        proposal = MatchProposal.of(request1, request2, null, NOW.plusSeconds(15));
        ReflectionTestUtils.setField(proposal, "id", 301L);
        when(proposalRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(proposal));
        when(requestRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(request1, request2));
    }

    @Test
    void restoresBothWaitingEntriesWithOnePairOperationAfterRejection() {
        MatchProposal result = service.decide(
                301L, request1.getId(), MatchProposalDecision.REJECTED, NOW
        );

        assertThat(result.getStatus()).isEqualTo(org.example.project2.domain.matching.entity.MatchProposalStatus.REJECTED);
        assertThat(request1.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(request2.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        verify(redisLifecycleService).restoreWaitingPairAfterCommit(request1, request2);
        verify(redisLifecycleService).removeProposalAfterCommit(301L);
    }

    @Test
    void removesWaitingAndProposalEntriesWhenMatchIsCompleted() {
        Match match = Match.of(request1, request2, NOW.plusSeconds(1));
        ReflectionTestUtils.setField(match, "id", 501L);
        ChatRoom chatRoom = ChatRoom.builder().match(match).build();
        ReflectionTestUtils.setField(chatRoom, "id", 601L);
        when(matchRepository.findByRequestPair(1L, 2L)).thenReturn(Optional.empty());
        when(matchRepository.save(org.mockito.ArgumentMatchers.any(Match.class))).thenReturn(match);
        when(chatRoomRepository.save(org.mockito.ArgumentMatchers.any(ChatRoom.class))).thenReturn(chatRoom);
        when(profileRepository.findAllByUserIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        MatchProposal firstDecision = service.decide(
                301L, request1.getId(), MatchProposalDecision.ACCEPTED, NOW);
        assertThat(firstDecision.getStatus())
                .isEqualTo(org.example.project2.domain.matching.entity.MatchProposalStatus.PENDING);
        assertThat(request1.getStatus()).isEqualTo(MatchRequestStatus.CONFIRMING);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());

        MatchProposal result = service.decide(
                301L, request2.getId(), MatchProposalDecision.ACCEPTED, NOW.plusSeconds(1));

        assertThat(result.getStatus()).isEqualTo(org.example.project2.domain.matching.entity.MatchProposalStatus.MATCHED);
        assertThat(request1.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(request2.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
        verify(redisLifecycleService).removeWaitingAfterCommit(request1, request2);
        verify(redisLifecycleService).removeProposalAfterCommit(301L);
        verify(requestRepository, times(2)).findAllByIdInForUpdate(List.of(1L, 2L));

        // 확정된 제안의 재실행은 이미 저장된 결과를 다시 만들지 않는다.
        assertThat(service.completeMatch(301L)).isSameAs(proposal);
    }

    @Test
    void doesNotScheduleRedisCleanupWhenChatRoomPersistenceFails() {
        Match match = Match.of(request1, request2, NOW.plusSeconds(1));
        ReflectionTestUtils.setField(match, "id", 502L);
        ChatRoom chatRoom = ChatRoom.builder().match(match).build();
        ReflectionTestUtils.setField(chatRoom, "id", 602L);
        when(matchRepository.findByRequestPair(1L, 2L)).thenReturn(Optional.empty());
        when(matchRepository.save(org.mockito.ArgumentMatchers.any(Match.class))).thenReturn(match);
        doThrow(new IllegalStateException("chat persistence failed"))
                .when(chatRoomRepository)
                .save(org.mockito.ArgumentMatchers.any(ChatRoom.class));
        proposal.decide(request2.getId(), MatchProposalDecision.ACCEPTED, NOW);

        assertThatThrownBy(() -> service.decide(
                301L, request1.getId(), MatchProposalDecision.ACCEPTED, NOW
        )).isInstanceOf(IllegalStateException.class);
        // DB 트랜잭션이 롤백되면 커밋 후 Redis 정리 콜백도 실행되지 않아야 한다.
        verify(redisLifecycleService, never()).removeWaitingAfterCommit(request1, request2);
        verify(redisLifecycleService, never()).removeProposalAfterCommit(301L);
    }

    @Test
    void restoresOnlyValidWaitingRequestsWhenAnotherMatchWinsTheRace() {
        Match existingMatch = Match.of(request1, request(3L), NOW.plusSeconds(1));
        ReflectionTestUtils.setField(existingMatch, "id", 503L);
        when(matchRepository.findByRequestPair(1L, 2L)).thenReturn(Optional.empty());
        when(matchRepository.findByRequestId(1L)).thenReturn(Optional.of(existingMatch));
        when(matchRepository.findByRequestId(2L)).thenReturn(Optional.empty());

        proposal.decide(request1.getId(), MatchProposalDecision.ACCEPTED, NOW);
        MatchProposal result = service.decide(
                301L, request2.getId(), MatchProposalDecision.ACCEPTED, NOW.plusSeconds(1)
        );

        assertThat(result.getStatus())
                .isEqualTo(org.example.project2.domain.matching.entity.MatchProposalStatus.CANCELLED);
        assertThat(request1.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(request2.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        verify(redisLifecycleService).removeWaitingAfterCommit(request1, request2);
        verify(redisLifecycleService).restoreWaitingPairAfterCommit(request1, request2);
        verify(redisLifecycleService).removeProposalAfterCommit(301L);
        verify(matchRepository, never()).save(org.mockito.ArgumentMatchers.any(Match.class));
    }

    private MatchRequest request(Long id) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("proposal-lifecycle-" + id + "-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hashed")
                .nickname("proposal-lifecycle-" + id)
                .build();
        MatchRequest request = MatchRequest.create(
                user,
                "KOREAN",
                NOW.plusSeconds(3_600),
                "11680",
                "서울특별시 강남구",
                "테스트 위치",
                point(127.039 + id / 100_000d, 37.501),
                3_000,
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK, PersonalityTag.ENJOY_DESSERT),
                null,
                "DESIRED_PERSONALITY_MATCH_V1"
        );
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private Point point(double longitude, double latitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
