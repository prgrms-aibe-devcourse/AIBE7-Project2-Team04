package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MatchProposalExpirationScheduler {
    private final MatchProposalRepository matchProposalRepository;
    private final MatchProposalLifecycleService lifecycleService;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.matching.proposal-reconcile-delay:1000}",
            initialDelayString = "${app.matching.proposal-reconcile-initial-delay:30000}"
    )
    public int expirePendingProposals() {
        Instant now = clock.instant();
        int expired = 0;
        for (MatchProposal proposal : matchProposalRepository
                .findAllByStatusAndExpiresAtBefore(MatchProposalStatus.PENDING, now)) {
            try {
                lifecycleService.expire(proposal.getId(), now);
                expired++;
            } catch (IllegalStateException ignored) {
                // 다른 요청 처리에서 먼저 종료된 제안은 다음 주기에 다시 조회하지 않습니다.
            }
        }
        return expired;
    }
}
