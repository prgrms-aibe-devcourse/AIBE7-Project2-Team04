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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
                redisLifecycleService,
                new MatchingProperties(Duration.ofMinutes(5), 3_000),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        request = request();
        ReflectionTestUtils.setField(request, "id", 42L);
        when(matchRequestRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(request));
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
