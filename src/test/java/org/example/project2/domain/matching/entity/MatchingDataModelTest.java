package org.example.project2.domain.matching.entity;

import jakarta.persistence.EntityManager;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.proposal.MatchProposalLifecycleService;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MatchingDataModelTest {
    private static final Instant NOW = Instant.parse("2026-08-27T03:00:00Z");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired UserRepository userRepository;
    @Autowired MatchRequestRepository matchRequestRepository;
    @Autowired MatchProposalRepository matchProposalRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchParticipantRepository matchParticipantRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MatchProposalLifecycleService matchProposalLifecycleService;
    @Autowired EntityManager entityManager;

    private User user1;
    private User user2;
    private MatchRequest request1;
    private MatchRequest request2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        user1 = saveUser("matching-a-" + suffix);
        user2 = saveUser("matching-b-" + suffix);
        request1 = matchRequestRepository.save(createRequest(user1, 127.039, 37.501));
        request2 = matchRequestRepository.save(createRequest(user2, 127.040, 37.502));
        matchRequestRepository.flush();
    }

    @Test
    void storesDesiredPersonalityVectorEmbedding() {
        float[] embedding = new float[1536];
        embedding[0] = 0.25f;
        request1.updateDesiredPersonalityEmbedding(embedding, "text-embedding-model", "v1", NOW);
        embedding[0] = 99.0f;

        matchRequestRepository.flush();
        Long requestId = request1.getId();
        entityManager.clear();

        MatchRequest reloaded = matchRequestRepository.findById(requestId).orElseThrow();
        assertThat(reloaded.getDesiredPersonalityEmbedding()).hasSize(1536);
        assertThat(reloaded.getDesiredPersonalityEmbedding()[0]).isEqualTo(0.25f);

        float[] returned = reloaded.getDesiredPersonalityEmbedding();
        returned[0] = 77.0f;
        assertThat(reloaded.getDesiredPersonalityEmbedding()[0]).isEqualTo(0.25f);
    }

    @Test
    void findsWaitingCandidatesWithinTheSourceRequestRadiusWithPostgis() {
        MatchRequest nearby = request2;
        User narrowRadiusUser = saveUser("matching-narrow-" + UUID.randomUUID());
        MatchRequest outsideCandidateRadius = matchRequestRepository.save(
                createRequest(narrowRadiusUser, 127.020, 37.500, 500)
        );
        User outsideUser = saveUser("matching-outside-" + UUID.randomUUID());
        MatchRequest outside = matchRequestRepository.save(createRequest(outsideUser, 127.100, 37.500));
        matchRequestRepository.flush();

        List<MatchRequest> candidates = matchRequestRepository.findWaitingCandidates(
                MatchRequestStatus.WAITING,
                request1.getId(),
                user1.getId()
        );

        assertThat(candidates).extracting(MatchRequest::getId)
                .contains(nearby.getId())
                .doesNotContain(outsideCandidateRadius.getId())
                .doesNotContain(outside.getId());
    }

    @Test
    void validatesEmbeddingDimensionAndFiniteValues() {
        assertThatThrownBy(() -> request1.updateDesiredPersonalityEmbedding(
                new float[10], "model", "v1", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("희망 설명 임베딩은 정확히 1536차원이어야 합니다.");

        float[] nonFinite = new float[1536];
        nonFinite[10] = Float.NaN;
        assertThatThrownBy(() -> request1.updateDesiredPersonalityEmbedding(
                nonFinite, "model", "v1", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("희망 설명 임베딩에는 유한한 값만 포함할 수 있습니다.");
    }

    @Test
    void proposalDecisionIsImmutableIdempotentAndRequiresBothAcceptances() {
        MatchProposal proposal = MatchProposal.of(
                request1, request2, scoreSnapshot(), NOW.plusSeconds(15));
        Long firstRequestId = proposal.getRequest1().getId();
        Long secondRequestId = proposal.getRequest2().getId();

        assertThatThrownBy(() -> proposal.decide(firstRequestId, MatchProposalDecision.PENDING, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        proposal.decide(firstRequestId, MatchProposalDecision.ACCEPTED, NOW);
        Instant firstDecisionAt = proposal.getRequest1DecidedAt();
        proposal.decide(firstRequestId, MatchProposalDecision.ACCEPTED, NOW.plusSeconds(1));
        assertThat(proposal.getRequest1DecidedAt()).isEqualTo(firstDecisionAt);

        assertThatThrownBy(() -> proposal.decide(
                firstRequestId, MatchProposalDecision.REJECTED, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 확정한 후보 제안 결정은 변경할 수 없습니다.");
        assertThatThrownBy(proposal::match)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("양쪽 사용자가 모두 수락한 제안만 매칭 완료 처리할 수 있습니다.");

        proposal.decide(secondRequestId, MatchProposalDecision.ACCEPTED, NOW.plusSeconds(1));
        proposal.match();
        assertThat(proposal.getStatus()).isEqualTo(MatchProposalStatus.MATCHED);
    }

    @Test
    void rejectsDecisionAtExpirationBoundary() {
        MatchProposal proposal = MatchProposal.of(request1, request2, scoreSnapshot(), NOW);

        assertThatThrownBy(() -> proposal.decide(
                proposal.getRequest1().getId(), MatchProposalDecision.ACCEPTED, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("응답 제한 시간이 만료된 제안입니다.");
        assertThat(proposal.getStatus()).isEqualTo(MatchProposalStatus.EXPIRED);
    }

    @Test
    void requestOnlyMatchesFromConfirmingAndTerminalStatesCannotOverwriteEachOther() {
        assertThatThrownBy(request1::match)
                .isInstanceOf(IllegalStateException.class);
        request1.startConfirming();
        request1.match();
        assertThatThrownBy(request1::cancel)
                .isInstanceOf(IllegalStateException.class);

        request2.cancel();
        request2.cancel();
        assertThatThrownBy(request2::expire)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blocksProposalAndMatchBetweenRequestsOwnedBySameUser() {
        MatchRequest anotherRequest = matchRequestRepository.saveAndFlush(
                createRequest(user1, 127.041, 37.503));

        assertThatThrownBy(() -> MatchProposal.of(
                request1, anotherRequest, scoreSnapshot(), NOW.plusSeconds(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("동일한 사용자에게 후보 제안을 생성할 수 없습니다.");
        assertThatThrownBy(() -> Match.of(request1, anotherRequest, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("동일한 사용자의 요청끼리는 매칭할 수 없습니다.");
    }

    @Test
    void storesCanonicalBidirectionalScoreSnapshot() {
        BidirectionalMatchScoreSnapshot input = scoreSnapshot();
        MatchProposal proposal = matchProposalRepository.saveAndFlush(
                MatchProposal.of(request2, request1, input, NOW.plusSeconds(15)));
        Long proposalId = proposal.getId();
        boolean request2IsCanonicalFirst = request2.getId() < request1.getId();
        entityManager.clear();

        BidirectionalMatchScoreSnapshot stored = matchProposalRepository.findById(proposalId)
                .orElseThrow()
                .getScoreSnapshot();
        assertThat(stored.sourceToTargetScore())
                .isEqualTo(request2IsCanonicalFirst ? input.sourceToTargetScore() : input.targetToSourceScore());
        assertThat(stored.targetToSourceScore())
                .isEqualTo(request2IsCanonicalFirst ? input.targetToSourceScore() : input.sourceToTargetScore());
        assertThat(stored.sourceToTargetMatchedTags())
                .isEqualTo(request2IsCanonicalFirst
                        ? input.sourceToTargetMatchedTags()
                        : input.targetToSourceMatchedTags());
        assertThat(stored.targetToSourceMatchedTags())
                .isEqualTo(request2IsCanonicalFirst
                        ? input.targetToSourceMatchedTags()
                        : input.sourceToTargetMatchedTags());
        assertThat(stored.pairScore()).isEqualTo((short) 75);
        assertThat(stored.formulaVersion()).isEqualTo("DESIRED_PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1");
    }

    @Test
    void databaseRejectsDuplicateProposalPair() {
        matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(15)));

        assertThatThrownBy(() -> matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(30))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionEndsProposalAndRestoresBothRequestsInOneTransaction() {
        request1.startConfirming();
        request2.startConfirming();
        MatchProposal proposal = matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(15)));

        matchProposalLifecycleService.decide(
                proposal.getId(), request1.getId(), MatchProposalDecision.REJECTED, NOW);
        entityManager.flush();
        entityManager.clear();

        MatchProposal reloadedProposal = matchProposalRepository.findById(proposal.getId()).orElseThrow();
        MatchRequest reloadedRequest1 = matchRequestRepository.findById(request1.getId()).orElseThrow();
        MatchRequest reloadedRequest2 = matchRequestRepository.findById(request2.getId()).orElseThrow();
        assertThat(reloadedProposal.getStatus()).isEqualTo(MatchProposalStatus.REJECTED);
        assertThat(reloadedRequest1.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(reloadedRequest2.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(reloadedRequest1.getRejectCount()).isEqualTo(1);
        assertThat(reloadedRequest2.getRejectCount()).isEqualTo(1);
    }

    @Test
    void expirationEndsProposalAndRestoresBothRequestsInOneTransaction() {
        request1.startConfirming();
        request2.startConfirming();
        MatchProposal proposal = matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(15)));

        matchProposalLifecycleService.expire(proposal.getId(), NOW.plusSeconds(15));
        entityManager.flush();
        entityManager.clear();

        MatchProposal reloadedProposal = matchProposalRepository.findById(proposal.getId()).orElseThrow();
        MatchRequest reloadedRequest1 = matchRequestRepository.findById(request1.getId()).orElseThrow();
        MatchRequest reloadedRequest2 = matchRequestRepository.findById(request2.getId()).orElseThrow();
        assertThat(reloadedProposal.getStatus()).isEqualTo(MatchProposalStatus.EXPIRED);
        assertThat(reloadedRequest1.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(reloadedRequest2.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
    }

    @Test
    void cancellationEndsProposalAndRestoresOnlyOtherRequestInOneTransaction() {
        request1.startConfirming();
        request2.startConfirming();
        MatchProposal proposal = matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(15)));

        matchProposalLifecycleService.cancelForRequest(proposal.getId(), request1.getId());
        entityManager.flush();
        entityManager.clear();

        MatchProposal reloadedProposal = matchProposalRepository.findById(proposal.getId()).orElseThrow();
        MatchRequest reloadedRequest1 = matchRequestRepository.findById(request1.getId()).orElseThrow();
        MatchRequest reloadedRequest2 = matchRequestRepository.findById(request2.getId()).orElseThrow();
        assertThat(reloadedProposal.getStatus()).isEqualTo(MatchProposalStatus.CANCELLED);
        assertThat(reloadedRequest1.getStatus()).isEqualTo(MatchRequestStatus.CANCELLED);
        assertThat(reloadedRequest2.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
    }

    @Test
    void createsOneMatchTwoParticipantsAndOneChatRoomOnlyAfterBothAccept() {
        request1.startConfirming();
        request2.startConfirming();
        MatchProposal proposal = matchProposalRepository.saveAndFlush(
                MatchProposal.of(request1, request2, scoreSnapshot(), NOW.plusSeconds(15)));

        matchProposalLifecycleService.decide(
                proposal.getId(), request1.getId(), MatchProposalDecision.ACCEPTED, NOW);
        entityManager.flush();

        assertThat(matchRepository.findByRequestId(request1.getId())).isEmpty();
        assertThat(matchParticipantRepository.findAllByMatchId(-1L)).isEmpty();

        matchProposalLifecycleService.decide(
                proposal.getId(), request2.getId(), MatchProposalDecision.ACCEPTED, NOW.plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        MatchProposal reloadedProposal = matchProposalRepository.findById(proposal.getId()).orElseThrow();
        MatchRequest reloadedRequest1 = matchRequestRepository.findById(request1.getId()).orElseThrow();
        MatchRequest reloadedRequest2 = matchRequestRepository.findById(request2.getId()).orElseThrow();
        Match match = matchRepository.findByRequestId(request1.getId()).orElseThrow();
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(match.getId()).orElseThrow();

        assertThat(reloadedProposal.getStatus()).isEqualTo(MatchProposalStatus.MATCHED);
        assertThat(reloadedRequest1.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(reloadedRequest2.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(matchParticipantRepository.findAllByMatchId(match.getId())).hasSize(2);
        assertThat(chatRoom.getMatch().getId()).isEqualTo(match.getId());

        // 동일한 완료 호출은 기존 결과를 재사용하며 추가 레코드를 만들지 않는다.
        matchProposalLifecycleService.completeMatch(proposal.getId());
        entityManager.flush();
        assertThat(matchRepository.findByRequestId(request1.getId())).containsSame(match);
        assertThat(matchParticipantRepository.findAllByMatchId(match.getId())).hasSize(2);
        assertThat(chatRoomRepository.findByMatchId(match.getId())).containsSame(chatRoom);
    }

    private User saveUser(String value) {
        return userRepository.save(User.builder()
                .email(value + "@test.com")
                .passwordHash("hashed")
                .nickname(value)
                .build());
    }

    private MatchRequest createRequest(User user, double longitude, double latitude) {
        return createRequest(user, longitude, latitude, 3000);
    }

    private MatchRequest createRequest(User user, double longitude, double latitude, int searchRadius) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        return MatchRequest.builder()
                .user(user)
                .foodCategory("한식")
                .mealAt(NOW.plusSeconds(3600))
                .regionCode("11680")
                .regionName("서울특별시 강남구")
                .locationName("테스트 위치")
                .location(point)
                .searchRadius(searchRadius)
                .desiredPersonalityTags(Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ))
                .desiredPersonalityText("편안하게 대화할 수 있는 분")
                .matchingFormulaVersion("DESIRED_PERSONALITY_MATCH_V1")
                .build();
    }

    private BidirectionalMatchScoreSnapshot scoreSnapshot() {
        return new BidirectionalMatchScoreSnapshot(
                (short) 80,
                List.of("대화 선호가 비슷해요"),
                List.of(PersonalityTag.GOOD_LISTENER),
                (short) 70,
                List.of("식사 속도 선호가 잘 맞아요"),
                List.of(PersonalityTag.FOOD_TALK),
                (short) 75,
                "DESIRED_PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1"
        );
    }
}
