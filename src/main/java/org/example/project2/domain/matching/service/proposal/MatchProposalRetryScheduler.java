package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchProposalRetryScheduler {
    private static final int BATCH_SIZE = 100;

    private final MatchRequestRepository matchRequestRepository;
    private final MatchProposalSelectionAttemptService attemptService;
    private final AtomicLong lastScannedRequestId = new AtomicLong(0L);

    @Scheduled(
            fixedDelayString = "${app.matching.proposal-search-delay:5000}",
            initialDelayString = "${app.matching.proposal-search-initial-delay:30000}"
    )
    public int retryWaitingRequests() {
        List<MatchRequest> requests;
        try {
            requests = findNextBatch();
        } catch (DataAccessException exception) {
            log.warn(
                    "실시간 매칭 재탐색 대상을 조회하지 못했습니다. errorType={}",
                    exception.getClass().getSimpleName()
            );
            return 0;
        }

        int created = 0;
        for (MatchRequest request : requests) {
            lastScannedRequestId.set(request.getId());
            if (request.getUser() == null || request.getUser().getId() == null) {
                continue;
            }
            MatchProposalSelectionAttemptService.AttemptResult result = attemptService.attempt(
                    request.getUser().getId(),
                    request.getId()
            );
            if (result == MatchProposalSelectionAttemptService.AttemptResult.CREATED) {
                created++;
            }
        }
        return created;
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

        // 마지막 ID까지 순회한 다음 첫 요청부터 다시 탐색합니다.
        lastScannedRequestId.set(0L);
        return matchRequestRepository.findAllByStatusAfterId(
                MatchRequestStatus.WAITING,
                0L,
                PageRequest.of(0, BATCH_SIZE)
        );
    }
}
