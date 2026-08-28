package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RealtimeMatchWaitingReconciliationService {
    private static final int BATCH_SIZE = 100;

    public enum RepairResult {
        PRESENT,
        RESTORED,
        EXPIRED,
        REDIS_UNAVAILABLE,
        NOT_WAITING
    }

    private final MatchRequestRepository matchRequestRepository;
    private final RealtimeMatchWaitingStore waitingStore;
    private final RealtimeMatchRedisLifecycleService redisLifecycleService;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.matching.waiting-reconcile-delay:5000}",
            initialDelayString = "${app.matching.waiting-reconcile-initial-delay:30000}"
    )
    @Transactional
    public int reconcileWaitingRequests() {
        cleanupExpiredGeoMembers();
        List<MatchRequest> requests = matchRequestRepository.findAllByStatus(
                MatchRequestStatus.WAITING,
                PageRequest.of(0, BATCH_SIZE)
        );
        int repaired = 0;
        for (MatchRequest request : requests) {
            RepairResult result = repair(request.getId());
            if (result == RepairResult.RESTORED || result == RepairResult.EXPIRED) {
                repaired++;
            }
        }
        return repaired;
    }

    private void cleanupExpiredGeoMembers() {
        try {
            waitingStore.cleanupExpiredGeoMembers();
        } catch (DataAccessException ignored) {
            // Redis 장애 중에는 다음 보정 주기에서 다시 정리합니다.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairResult repair(Long requestId) {
        if (requestId == null) {
            return RepairResult.NOT_WAITING;
        }
        MatchRequest request = matchRequestRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.isWaiting()) {
            return RepairResult.NOT_WAITING;
        }

        Optional<Duration> redisTtl;
        try {
            redisTtl = Optional.ofNullable(waitingStore.remainingTtl(requestId))
                    .orElse(Optional.empty());
        } catch (DataAccessException ignored) {
            // Redis 장애 중에는 DB 상태를 임의로 만료시키지 않습니다.
            return RepairResult.REDIS_UNAVAILABLE;
        }
        if (redisTtl.isPresent()) {
            return RepairResult.PRESENT;
        }

        Duration remaining = remainingWaitingTtl(request);
        if (remaining.isZero() || remaining.isNegative()) {
            request.expire();
            redisLifecycleService.removeWaitingAfterCommit(request);
            return RepairResult.EXPIRED;
        }

        // Redis 키가 사라졌지만 DB 기준 대기 시간이 남아 있으면 Geo/TTL 키를 복구합니다.
        return redisLifecycleService.restoreWaiting(request, remaining)
                ? RepairResult.RESTORED
                : RepairResult.REDIS_UNAVAILABLE;
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
