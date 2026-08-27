package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MatchProposalCreatedEventHandler {
    private final MatchProposalWebSocketPublisher webSocketPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchProposalCreatedEvent event) {
        webSocketPublisher.publish(event);
    }
}
