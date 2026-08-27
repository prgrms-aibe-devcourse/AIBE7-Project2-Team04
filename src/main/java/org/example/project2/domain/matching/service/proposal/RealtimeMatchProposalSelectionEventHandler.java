package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeMatchProposalSelectionEventHandler {
    private final MatchProposalSelectionService matchProposalSelectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RealtimeMatchRequestWaitingEvent event) {
        try {
            matchProposalSelectionService.selectAndCreate(event.userId(), event.requestId());
        } catch (RuntimeException exception) {
            log.warn("실시간 매칭 후보 제안 생성에 실패했습니다. requestId={}", event.requestId(), exception);
        }
    }
}
