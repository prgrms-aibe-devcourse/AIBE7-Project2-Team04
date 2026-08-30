package org.example.project2.domain.matching.service.monitoring;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.service.proposal.MatchResultCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 최종 매칭 트랜잭션이 커밋된 뒤에만 완료 수를 증가시킵니다.
 */
@Component
@RequiredArgsConstructor
public class MatchCompletedMetricsEventHandler {
    private final MatchingMetrics matchingMetrics;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchResultCreatedEvent event) {
        matchingMetrics.recordCompleted();
    }
}
