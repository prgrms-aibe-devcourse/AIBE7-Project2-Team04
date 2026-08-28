package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchProposalStore;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.reset;

/**
 * PostgreSQL에서 실제 두 개의 독립 트랜잭션이 같은 제안과 요청을 동시에 처리하는지 검증한다.
 *
 * <p>Redis와 WebSocket은 이 테스트의 대상이 아니므로 외부 시스템 호출만 대체하고,
 * 매칭·참여자·채팅방 생성은 실제 데이터베이스에서 수행한다.</p>
 */
@SpringBootTest
class MatchProposalConcurrencyIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Set<PersonalityTag> DESIRED_TAGS = Set.of(
            PersonalityTag.GOOD_LISTENER,
            PersonalityTag.FOOD_TALK,
            PersonalityTag.ENJOY_DESSERT
    );

    @Autowired UserRepository userRepository;
    @Autowired MatchRequestRepository matchRequestRepository;
    @Autowired MatchProposalRepository matchProposalRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchParticipantRepository matchParticipantRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MatchProposalLifecycleService lifecycleService;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean RealtimeMatchWaitingStore waitingStore;
    @MockitoBean RealtimeMatchProposalStore proposalStore;
    @MockitoBean MatchProposalWebSocketPublisher proposalWebSocketPublisher;
    @MockitoBean MatchResultWebSocketPublisher resultWebSocketPublisher;

    private final Set<UUID> createdUserIds = new HashSet<>();
    private final Set<Long> createdRequestIds = new HashSet<>();
    private final Set<Long> createdProposalIds = new HashSet<>();

    @BeforeEach
    void setUp() {
        reset(waitingStore, proposalStore, proposalWebSocketPublisher, resultWebSocketPublisher);
    }

    @AfterEach
    void tearDown() {
        cleanupFixtures();
    }

    @Test
    void twoIndependentTransactionsAcceptingSameProposalCreateOneMatch() throws Exception {
        PairFixture fixture = createPair(Instant.now().plus(Duration.ofMinutes(5)));
        Instant acceptedAt = Instant.now();

        List<DecisionOutcome> outcomes = runConcurrently(
                () -> accept(fixture.proposalId(), fixture.request1Id(), acceptedAt),
                () -> accept(fixture.proposalId(), fixture.request2Id(), acceptedAt.plusMillis(1))
        );

        assertSuccessful(outcomes);
        MatchState state = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        assertThat(state.matchIds()).hasSize(1);
        assertThat(state.participantCount()).isEqualTo(2);
        assertThat(state.chatRoomCount()).isEqualTo(1);
        assertThat(state.proposalStatuses()).containsEntry(fixture.proposalId(), MatchProposalStatus.MATCHED);
        assertThat(state.requestStatuses().values())
                .containsOnly(MatchRequestStatus.MATCHED);
    }

    @Test
    void duplicateAcceptanceRetryReturnsExistingMatchedResultWithoutDuplicates() throws Exception {
        PairFixture fixture = createPair(Instant.now().plus(Duration.ofMinutes(5)));
        Instant acceptedAt = Instant.now();
        assertSuccessful(runConcurrently(
                () -> accept(fixture.proposalId(), fixture.request1Id(), acceptedAt),
                () -> accept(fixture.proposalId(), fixture.request2Id(), acceptedAt.plusMillis(1))
        ));

        MatchState beforeRetry = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        List<DecisionOutcome> retries = runConcurrently(
                () -> accept(fixture.proposalId(), fixture.request1Id(), acceptedAt.plusSeconds(1)),
                () -> accept(fixture.proposalId(), fixture.request1Id(), acceptedAt.plusSeconds(1))
        );

        assertSuccessful(retries);
        assertThat(retries).allSatisfy(outcome ->
                assertThat(outcome.status()).isEqualTo(MatchProposalStatus.MATCHED));
        MatchState afterRetry = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        assertThat(afterRetry.matchIds()).isEqualTo(beforeRetry.matchIds());
        assertThat(afterRetry.participantCount()).isEqualTo(beforeRetry.participantCount());
        assertThat(afterRetry.chatRoomCount()).isEqualTo(beforeRetry.chatRoomCount());
    }

    @Test
    void competingProposalsCannotUseOneRequestTwice() throws Exception {
        CompetitionFixture fixture = createCompetition();
        Instant acceptedAt = Instant.now();

        List<DecisionOutcome> outcomes = runConcurrently(
                () -> accept(fixture.firstProposalId(), fixture.otherRequestId(), acceptedAt),
                () -> accept(fixture.secondProposalId(), fixture.thirdRequestId(), acceptedAt.plusMillis(1))
        );

        assertSuccessful(outcomes);
        MatchState state = readState(
                List.of(fixture.firstRequestId(), fixture.otherRequestId(), fixture.thirdRequestId()),
                List.of(fixture.firstProposalId(), fixture.secondProposalId())
        );
        assertThat(state.matchIds()).hasSize(1);
        assertThat(state.participantCount()).isEqualTo(2);
        assertThat(state.chatRoomCount()).isEqualTo(1);
        assertThat(state.proposalStatuses().values())
                .containsExactlyInAnyOrder(MatchProposalStatus.MATCHED, MatchProposalStatus.CANCELLED);
        assertThat(state.requestStatuses().get(fixture.firstRequestId()))
                .as("A 요청 상태: %s, 전체 상태: %s", state.requestStatuses().get(fixture.firstRequestId()), state.requestStatuses())
                .isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(state.requestStatuses().values())
                .contains(MatchRequestStatus.MATCHED)
                .contains(MatchRequestStatus.WAITING);
    }

    @Test
    void rejectionRacingWithAcceptanceCannotCreateMatch() throws Exception {
        PairFixture fixture = createPair(Instant.now().plus(Duration.ofMinutes(5)));
        List<DecisionOutcome> outcomes = runConcurrently(
                () -> reject(fixture.proposalId(), fixture.request1Id()),
                () -> accept(fixture.proposalId(), fixture.request2Id(), Instant.now())
        );

        assertAtLeastOneSuccessful(outcomes);
        MatchState state = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        assertThat(state.matchIds()).isEmpty();
        assertThat(state.participantCount()).isZero();
        assertThat(state.chatRoomCount()).isZero();
        assertThat(state.proposalStatuses()).containsEntry(fixture.proposalId(), MatchProposalStatus.REJECTED);
        assertThat(state.requestStatuses().values()).containsOnly(MatchRequestStatus.WAITING);
    }

    @Test
    void cancellationRacingWithAcceptanceCannotCreateMatch() throws Exception {
        PairFixture fixture = createPair(Instant.now().plus(Duration.ofMinutes(5)));
        List<DecisionOutcome> outcomes = runConcurrently(
                () -> cancel(fixture.proposalId(), fixture.request1Id()),
                () -> accept(fixture.proposalId(), fixture.request2Id(), Instant.now())
        );

        assertAtLeastOneSuccessful(outcomes);
        MatchState state = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        assertThat(state.matchIds()).isEmpty();
        assertThat(state.participantCount()).isZero();
        assertThat(state.chatRoomCount()).isZero();
        assertThat(state.proposalStatuses()).containsEntry(fixture.proposalId(), MatchProposalStatus.CANCELLED);
        assertThat(state.requestStatuses().get(fixture.request1Id()))
                .isEqualTo(MatchRequestStatus.CANCELLED);
        assertThat(state.requestStatuses().get(fixture.request2Id()))
                .isEqualTo(MatchRequestStatus.WAITING);
    }

    @Test
    void expirationRacingWithAcceptanceCannotCreateMatch() throws Exception {
        Instant expiredAt = Instant.now().minusSeconds(5);
        PairFixture fixture = createPair(expiredAt);
        List<DecisionOutcome> outcomes = runConcurrently(
                () -> expire(fixture.proposalId(), Instant.now()),
                () -> accept(fixture.proposalId(), fixture.request2Id(), Instant.now())
        );

        assertAtLeastOneSuccessful(outcomes);
        MatchState state = readState(
                List.of(fixture.request1Id(), fixture.request2Id()),
                List.of(fixture.proposalId())
        );
        assertThat(state.matchIds()).isEmpty();
        assertThat(state.participantCount()).isZero();
        assertThat(state.chatRoomCount()).isZero();
        assertThat(state.proposalStatuses()).containsEntry(fixture.proposalId(), MatchProposalStatus.EXPIRED);
        assertThat(state.requestStatuses().values()).containsOnly(MatchRequestStatus.WAITING);
    }

    private DecisionOutcome accept(Long proposalId, Long requestId, Instant acceptedAt) {
        try {
            MatchProposal proposal = transactionTemplate.execute(status -> lifecycleService.decide(
                    proposalId,
                    requestId,
                    MatchProposalDecision.ACCEPTED,
                    acceptedAt
            ));
            return new DecisionOutcome(proposal.getStatus(), null);
        } catch (Throwable failure) {
            return new DecisionOutcome(null, failure);
        }
    }

    private DecisionOutcome reject(Long proposalId, Long requestId) {
        try {
            MatchProposal proposal = transactionTemplate.execute(status -> lifecycleService.decide(
                    proposalId,
                    requestId,
                    MatchProposalDecision.REJECTED,
                    Instant.now()
            ));
            return new DecisionOutcome(proposal.getStatus(), null);
        } catch (Throwable failure) {
            return new DecisionOutcome(null, failure);
        }
    }

    private DecisionOutcome cancel(Long proposalId, Long requestId) {
        try {
            MatchProposal proposal = transactionTemplate.execute(status ->
                    lifecycleService.cancelForRequest(proposalId, requestId));
            return new DecisionOutcome(proposal.getStatus(), null);
        } catch (Throwable failure) {
            return new DecisionOutcome(null, failure);
        }
    }

    private DecisionOutcome expire(Long proposalId, Instant expiredAt) {
        try {
            MatchProposal proposal = transactionTemplate.execute(status ->
                    lifecycleService.expire(proposalId, expiredAt));
            return new DecisionOutcome(proposal.getStatus(), null);
        } catch (Throwable failure) {
            return new DecisionOutcome(null, failure);
        }
    }

    private List<DecisionOutcome> runConcurrently(
            Supplier<DecisionOutcome> firstOperation,
            Supplier<DecisionOutcome> secondOperation
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<DecisionOutcome> first = executor.submit(awaitStart(ready, start, firstOperation));
        Future<DecisionOutcome> second = executor.submit(awaitStart(ready, start, secondOperation));
        try {
            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("두 작업이 모두 독립 트랜잭션을 시작할 준비를 마쳐야 합니다")
                    .isTrue();
            start.countDown();
            return List.of(
                    getWithTimeout(first),
                    getWithTimeout(second)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                fail("동시성 테스트 작업이 종료되지 않았습니다.");
            }
        }
    }

    private Callable<DecisionOutcome> awaitStart(
            CountDownLatch ready,
            CountDownLatch start,
            Supplier<DecisionOutcome> operation
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 시작 신호를 받지 못했습니다.");
            }
            return operation.get();
        };
    }

    private DecisionOutcome getWithTimeout(Future<DecisionOutcome> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(20, TimeUnit.SECONDS);
    }

    private void assertSuccessful(List<DecisionOutcome> outcomes) {
        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.failure()).as("동시 트랜잭션이 실패하지 않아야 합니다").isNull());
    }

    private void assertAtLeastOneSuccessful(List<DecisionOutcome> outcomes) {
        assertThat(outcomes).anyMatch(outcome -> outcome.failure() == null);
    }

    private PairFixture createPair(Instant expiresAt) {
        PairFixture fixture = transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User first = userRepository.saveAndFlush(newUser("concurrent-a-" + suffix));
            User second = userRepository.saveAndFlush(newUser("concurrent-b-" + suffix));
            MatchRequest firstRequest = matchRequestRepository.save(
                    createRequest(first, 127.039, 37.501));
            MatchRequest secondRequest = matchRequestRepository.save(
                    createRequest(second, 127.040, 37.502));
            matchRequestRepository.flush();
            firstRequest.startConfirming();
            secondRequest.startConfirming();
            MatchProposal proposal = matchProposalRepository.saveAndFlush(
                    MatchProposal.of(firstRequest, secondRequest, scoreSnapshot(), expiresAt));
            return new PairFixture(
                    first.getId(),
                    second.getId(),
                    firstRequest.getId(),
                    secondRequest.getId(),
                    proposal.getId()
            );
        });
        register(fixture);
        return fixture;
    }

    private CompetitionFixture createCompetition() {
        CompetitionFixture fixture = transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User first = userRepository.saveAndFlush(newUser("competition-a-" + suffix));
            User second = userRepository.saveAndFlush(newUser("competition-b-" + suffix));
            User third = userRepository.saveAndFlush(newUser("competition-c-" + suffix));
            MatchRequest firstRequest = matchRequestRepository.save(
                    createRequest(first, 127.039, 37.501));
            MatchRequest secondRequest = matchRequestRepository.save(
                    createRequest(second, 127.040, 37.502));
            MatchRequest thirdRequest = matchRequestRepository.save(
                    createRequest(third, 127.041, 37.503));
            matchRequestRepository.flush();
            firstRequest.startConfirming();
            secondRequest.startConfirming();
            thirdRequest.startConfirming();

            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
            MatchProposal firstProposal = MatchProposal.of(
                    firstRequest, secondRequest, scoreSnapshot(), expiresAt);
            MatchProposal secondProposal = MatchProposal.of(
                    firstRequest, thirdRequest, scoreSnapshot(), expiresAt);
            firstProposal.decide(firstRequest.getId(), MatchProposalDecision.ACCEPTED, Instant.now());
            secondProposal.decide(firstRequest.getId(), MatchProposalDecision.ACCEPTED, Instant.now());
            matchProposalRepository.save(firstProposal);
            matchProposalRepository.saveAndFlush(secondProposal);
            return new CompetitionFixture(
                    first.getId(),
                    second.getId(),
                    third.getId(),
                    firstRequest.getId(),
                    secondRequest.getId(),
                    thirdRequest.getId(),
                    firstProposal.getId(),
                    secondProposal.getId()
            );
        });
        register(fixture);
        return fixture;
    }

    private User newUser(String nickname) {
        return User.builder()
                .email(nickname + "@test.com")
                .passwordHash("hashed")
                .nickname(nickname)
                .build();
    }

    private MatchRequest createRequest(User user, double longitude, double latitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return MatchRequest.builder()
                .user(user)
                .foodCategory("한식")
                .mealAt(Instant.now().plus(Duration.ofHours(1)))
                .regionCode("11680")
                .regionName("서울특별시 강남구")
                .locationName("테스트 위치")
                .location(point)
                .searchRadius(3_000)
                .desiredPersonalityTags(DESIRED_TAGS)
                .desiredPersonalityText("편안하게 대화할 수 있는 분")
                .matchingFormulaVersion("DESIRED_PERSONALITY_MATCH_V1")
                .build();
    }

    private org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot scoreSnapshot() {
        return new org.example.project2.domain.matching.dto.scoring.BidirectionalMatchScoreSnapshot(
                (short) 80,
                List.of("대화 성향이 비슷해요"),
                List.of(PersonalityTag.GOOD_LISTENER),
                (short) 70,
                List.of("식사 속도 선호가 맞아요"),
                List.of(PersonalityTag.FOOD_TALK),
                (short) 75,
                "DESIRED_PERSONALITY_MATCH_V1_BIDIRECTIONAL_MIN_V1"
        );
    }

    private MatchState readState(List<Long> requestIds, List<Long> proposalIds) {
        return transactionTemplate.execute(status -> {
            Set<Long> matchIds = new HashSet<>();
            Map<Long, MatchRequestStatus> requestStatuses = new HashMap<>();
            for (Long requestId : requestIds) {
                matchRequestRepository.findById(requestId)
                        .ifPresent(request -> requestStatuses.put(requestId, request.getStatus()));
                matchRepository.findByRequestId(requestId)
                        .map(Match::getId)
                        .ifPresent(matchIds::add);
            }
            int participantCount = matchIds.stream()
                    .mapToInt(matchId -> matchParticipantRepository.findAllByMatchId(matchId).size())
                    .sum();
            int chatRoomCount = (int) matchIds.stream()
                    .filter(matchId -> chatRoomRepository.findByMatchId(matchId).isPresent())
                    .count();
            Map<Long, MatchProposalStatus> proposalStatuses = new HashMap<>();
            proposalIds.forEach(proposalId -> matchProposalRepository.findById(proposalId)
                    .ifPresent(proposal -> proposalStatuses.put(proposalId, proposal.getStatus())));
            return new MatchState(
                    matchIds,
                    participantCount,
                    chatRoomCount,
                    requestStatuses,
                    proposalStatuses
            );
        });
    }

    private void register(PairFixture fixture) {
        createdUserIds.add(fixture.firstUserId());
        createdUserIds.add(fixture.secondUserId());
        createdRequestIds.add(fixture.request1Id());
        createdRequestIds.add(fixture.request2Id());
        createdProposalIds.add(fixture.proposalId());
    }

    private void register(CompetitionFixture fixture) {
        createdUserIds.add(fixture.firstUserId());
        createdUserIds.add(fixture.secondUserId());
        createdUserIds.add(fixture.thirdUserId());
        createdRequestIds.add(fixture.firstRequestId());
        createdRequestIds.add(fixture.otherRequestId());
        createdRequestIds.add(fixture.thirdRequestId());
        createdProposalIds.add(fixture.firstProposalId());
        createdProposalIds.add(fixture.secondProposalId());
    }

    private void cleanupFixtures() {
        if (createdUserIds.isEmpty() && createdRequestIds.isEmpty()
                && createdProposalIds.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Set<Long> matchIds = new HashSet<>();
            for (Long requestId : createdRequestIds) {
                matchRepository.findByRequestId(requestId)
                        .map(Match::getId)
                        .ifPresent(matchIds::add);
            }
            for (Long matchId : matchIds) {
                chatRoomRepository.findByMatchId(matchId).ifPresent(chatRoomRepository::delete);
                matchParticipantRepository.deleteAll(matchParticipantRepository.findAllByMatchId(matchId));
            }
            chatRoomRepository.flush();
            matchParticipantRepository.flush();
            for (Long matchId : matchIds) {
                matchRepository.findById(matchId).ifPresent(matchRepository::delete);
            }
            matchRepository.flush();
            matchProposalRepository.deleteAllById(new ArrayList<>(createdProposalIds));
            matchProposalRepository.flush();
            matchRequestRepository.deleteAllById(new ArrayList<>(createdRequestIds));
            matchRequestRepository.flush();
            userRepository.deleteAllById(new ArrayList<>(createdUserIds));
            userRepository.flush();
        });
        createdUserIds.clear();
        createdRequestIds.clear();
        createdProposalIds.clear();
    }

    private record PairFixture(
            UUID firstUserId,
            UUID secondUserId,
            Long request1Id,
            Long request2Id,
            Long proposalId
    ) {
    }

    private record CompetitionFixture(
            UUID firstUserId,
            UUID secondUserId,
            UUID thirdUserId,
            Long firstRequestId,
            Long otherRequestId,
            Long thirdRequestId,
            Long firstProposalId,
            Long secondProposalId
    ) {
    }

    private record DecisionOutcome(MatchProposalStatus status, Throwable failure) {
    }

    private record MatchState(
            Set<Long> matchIds,
            int participantCount,
            int chatRoomCount,
            Map<Long, MatchRequestStatus> requestStatuses,
            Map<Long, MatchProposalStatus> proposalStatuses
    ) {
    }
}
