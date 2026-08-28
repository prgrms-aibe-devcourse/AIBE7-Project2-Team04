package org.example.project2.domain.matching.service.request;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMatchWaitingReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock MatchRequestRepository matchRequestRepository;
    @Mock RealtimeMatchWaitingStore waitingStore;
    @Mock RealtimeMatchRedisLifecycleService redisLifecycleService;

    private RealtimeMatchWaitingReconciliationService service;
    private MatchRequest request;

    @BeforeEach
    void setUp() {
        service = new RealtimeMatchWaitingReconciliationService(
                matchRequestRepository,
                waitingStore,
                new RealtimeMatchWaitingRepairService(
                        matchRequestRepository,
                        waitingStore,
                        redisLifecycleService,
                        new MatchingProperties(Duration.ofMinutes(5), 3_000),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
        request = request();
        ReflectionTestUtils.setField(request, "id", 42L);
        lenient().when(matchRequestRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
    }

    @Test
    void restoresMissingRedisEntryWhenDbWaitingTtlRemains() {
        ReflectionTestUtils.setField(request, "updatedAt", NOW.minusSeconds(30));
        when(waitingStore.remainingTtl(42L)).thenReturn(Optional.empty());
        when(redisLifecycleService.restoreWaiting(request, Duration.ofSeconds(270))).thenReturn(true);

        assertThat(service.repair(42L))
                .isEqualTo(RealtimeMatchWaitingReconciliationService.RepairResult.RESTORED);
        verify(redisLifecycleService).restoreWaiting(request, Duration.ofSeconds(270));
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
    }

    @Test
    void expiresDbRequestWhenRedisAndDbTtlHaveBothElapsed() {
        ReflectionTestUtils.setField(request, "updatedAt", NOW.minusSeconds(301));
        when(waitingStore.remainingTtl(42L)).thenReturn(Optional.empty());

        assertThat(service.repair(42L))
                .isEqualTo(RealtimeMatchWaitingReconciliationService.RepairResult.EXPIRED);
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.EXPIRED);
        verify(redisLifecycleService).removeWaitingAfterCommit(request);
        verify(redisLifecycleService, never()).restoreWaiting(any(), any());
    }

    @Test
    void doesNotChangeDbStateDuringRedisOutage() {
        when(waitingStore.remainingTtl(42L))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThat(service.repair(42L))
                .isEqualTo(RealtimeMatchWaitingReconciliationService.RepairResult.REDIS_UNAVAILABLE);
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.WAITING);
        verify(redisLifecycleService, never()).removeWaitingAfterCommit(any());
    }

    @Test
    void processesRequestsAfterFirstHundredAcrossReconciliationCycles() {
        List<MatchRequest> firstBatch = new ArrayList<>();
        List<MatchRequest> requestsById = new ArrayList<>();
        IntStream.rangeClosed(1, 101).forEach(id -> {
            MatchRequest waitingRequest = org.mockito.Mockito.mock(MatchRequest.class);
            when(waitingRequest.getId()).thenReturn((long) id);
            when(waitingRequest.isWaiting()).thenReturn(true);
            requestsById.add(waitingRequest);
            if (id <= 100) {
                firstBatch.add(waitingRequest);
            }
        });
        when(matchRequestRepository.findAllByStatusAfterId(
                org.mockito.ArgumentMatchers.eq(MatchRequestStatus.WAITING),
                org.mockito.ArgumentMatchers.eq(0L),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(firstBatch);
        when(matchRequestRepository.findAllByStatusAfterId(
                org.mockito.ArgumentMatchers.eq(MatchRequestStatus.WAITING),
                org.mockito.ArgumentMatchers.eq(100L),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(List.of(requestsById.get(100)));
        when(matchRequestRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> Optional.of(requestsById.get(
                        Math.toIntExact((Long) invocation.getArgument(0)) - 1
                )));
        when(waitingStore.remainingTtl(anyLong())).thenReturn(Optional.of(Duration.ofSeconds(100)));

        assertThat(service.reconcileWaitingRequests()).isZero();
        assertThat(service.reconcileWaitingRequests()).isZero();

        verify(matchRequestRepository).findAllByStatusAfterId(
                MatchRequestStatus.WAITING,
                100L,
                org.springframework.data.domain.PageRequest.of(0, 100)
        );
        verify(matchRequestRepository, org.mockito.Mockito.times(101)).findByIdForUpdate(any());
    }

    @Test
    void continuesWithNextRequestWhenOneRepairFails() {
        RealtimeMatchWaitingRepairService repairService = org.mockito.Mockito.mock(
                RealtimeMatchWaitingRepairService.class
        );
        RealtimeMatchWaitingReconciliationService batchService =
                new RealtimeMatchWaitingReconciliationService(
                        matchRequestRepository,
                        waitingStore,
                        repairService
                );
        MatchRequest first = org.mockito.Mockito.mock(MatchRequest.class);
        MatchRequest second = org.mockito.Mockito.mock(MatchRequest.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(matchRequestRepository.findAllByStatusAfterId(
                org.mockito.ArgumentMatchers.eq(MatchRequestStatus.WAITING),
                org.mockito.ArgumentMatchers.eq(0L),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(List.of(first, second));
        when(repairService.repair(1L)).thenThrow(new IllegalStateException("temporary"));
        when(repairService.repair(2L)).thenReturn(
                RealtimeMatchWaitingReconciliationService.RepairResult.RESTORED
        );

        assertThat(batchService.reconcileWaitingRequests()).isEqualTo(1);

        verify(repairService).repair(1L);
        verify(repairService).repair(2L);
    }

    private MatchRequest request() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("reconcile-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hashed")
                .nickname("reconcile")
                .build();
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(127.039, 37.501));
        point.setSRID(4326);
        return MatchRequest.create(
                user,
                "KOREAN",
                NOW.plusSeconds(3_600),
                "11680",
                "서울특별시 강남구",
                "테스트 위치",
                point,
                3_000,
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK, PersonalityTag.ENJOY_DESSERT),
                null,
                "DESIRED_PERSONALITY_MATCH_V1"
        );
    }
}
