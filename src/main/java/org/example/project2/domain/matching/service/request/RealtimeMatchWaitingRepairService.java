package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 단일 대기 요청의 Redis·DB 정합성을 복구합니다.
 *
 * <p>호출마다 별도 트랜잭션을 사용하여 한 요청의 잠금이나 실패가
 * 같은 배치의 다른 요청으로 전파되지 않도록 합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeMatchWaitingRepairService {
    private final MatchRequestRepository matchRequestRepository;
    private final RealtimeMatchWaitingStore waitingStore;
    private final RealtimeMatchRedisLifecycleService redisLifecycleService;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RealtimeMatchWaitingReconciliationService.RepairResult repair(Long requestId) {
        if (requestId == null) {
            return RealtimeMatchWaitingReconciliationService.RepairResult.NOT_WAITING;
        }
        MatchRequest request = matchRequestRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.isWaiting()) {
            return RealtimeMatchWaitingReconciliationService.RepairResult.NOT_WAITING;
        }

        Optional<Duration> redisTtl;
        try {
            redisTtl = Optional.ofNullable(waitingStore.remainingTtl(requestId))
                    .orElse(Optional.empty());
        } catch (DataAccessException ignored) {
            // Redis 장애 중에는 DB 상태를 임의로 만료시키지 않습니다.
            log.debug("Redis 대기 상태를 확인하지 못해 DB 변경을 보류합니다. errorCode=MATCHING_REDIS_UNAVAILABLE");
            return RealtimeMatchWaitingReconciliationService.RepairResult.REDIS_UNAVAILABLE;
        }
        if (redisTtl.isPresent()) {
            return RealtimeMatchWaitingReconciliationService.RepairResult.PRESENT;
        }

        Duration remaining = remainingWaitingTtl(request);
        if (remaining.isZero() || remaining.isNegative()) {
            request.expire();
            redisLifecycleService.removeWaitingAfterCommit(request);
            return RealtimeMatchWaitingReconciliationService.RepairResult.EXPIRED;
        }

        // Redis 키가 사라졌지만 DB 기준 대기 시간이 남아 있으면 Geo/TTL 키를 복구합니다.
        return redisLifecycleService.restoreWaiting(request, remaining)
                ? RealtimeMatchWaitingReconciliationService.RepairResult.RESTORED
                : RealtimeMatchWaitingReconciliationService.RepairResult.REDIS_UNAVAILABLE;
    }

    private Duration remainingWaitingTtl(MatchRequest request) {
        Instant waitingSince = request.getUpdatedAt() != null
                ? request.getUpdatedAt()
                : request.getCreatedAt();
        if (waitingSince == null) {
            return matchingProperties.waitingTtl();
        }
        return matchingProperties.waitingTtl().minus(Duration.between(waitingSince, clock.instant()));
    }
}
