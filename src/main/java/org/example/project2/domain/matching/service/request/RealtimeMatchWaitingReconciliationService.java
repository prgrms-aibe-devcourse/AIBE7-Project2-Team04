package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final RealtimeMatchWaitingRepairService repairService;
    private final AtomicLong lastScannedRequestId = new AtomicLong(0L);

    @Scheduled(
            fixedDelayString = "${app.matching.waiting-reconcile-delay:5000}",
            initialDelayString = "${app.matching.waiting-reconcile-initial-delay:30000}"
    )
    public int reconcileWaitingRequests() {
        cleanupExpiredGeoMembers();
        List<MatchRequest> requests;
        try {
            requests = findNextBatch();
        } catch (DataAccessException ignored) {
            log.warn(
                    "Redis 대기 재조정 대상을 조회하지 못했습니다. errorCode=MATCHING_REDIS_UNAVAILABLE"
            );
            return 0;
        }
        int repaired = 0;
        for (MatchRequest request : requests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            lastScannedRequestId.set(request.getId());
            try {
                RepairResult result = repairService.repair(request.getId());
                if (result == RepairResult.RESTORED || result == RepairResult.EXPIRED) {
                    repaired++;
                }
            } catch (RuntimeException ignored) {
                // 한 요청의 DB 잠금·일시 오류가 배치 전체를 중단시키지 않도록 다음 요청으로 진행합니다.
                log.warn(
                        "대기 요청 재조정을 다음 주기로 보류합니다. errorCode=MATCHING_RECONCILIATION_RETRY"
                );
            }
        }
        return repaired;
    }

    private List<MatchRequest> findNextBatch() {
        List<MatchRequest> requests = matchRequestRepository.findAllByStatusAfterId(
                MatchRequestStatus.WAITING,
                lastScannedRequestId.get(),
                PageRequest.of(0, BATCH_SIZE)
        );
        if (!requests.isEmpty() || lastScannedRequestId.get() == 0L) {
            return requests;
        }

        // 마지막 ID까지 순회하면 다음 배치에서 첫 요청부터 다시 확인합니다.
        lastScannedRequestId.set(0L);
        return matchRequestRepository.findAllByStatusAfterId(
                MatchRequestStatus.WAITING,
                0L,
                PageRequest.of(0, BATCH_SIZE)
        );
    }

    private void cleanupExpiredGeoMembers() {
        try {
            waitingStore.cleanupExpiredGeoMembers();
        } catch (DataAccessException ignored) {
            // Redis 장애 중에는 다음 보정 주기에서 다시 정리합니다.
        }
    }

    public RepairResult repair(Long requestId) {
        return repairService.repair(requestId);
    }
}
