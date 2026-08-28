package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.RealtimeMatchProposalStore;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingEntry;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RealtimeMatchRedisLifecycleService {
    private final RealtimeMatchWaitingStore waitingStore;
    private final RealtimeMatchProposalStore proposalStore;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    public void removeWaitingAfterCommit(MatchRequest... requests) {
        afterCommit(() -> {
            for (MatchRequest request : requests) {
                if (request == null || request.getId() == null || request.getUser() == null
                        || request.getUser().getId() == null) {
                    continue;
                }
                try {
                    waitingStore.remove(request.getUser().getId(), request.getId());
                } catch (DataAccessException ignored) {
                    // DB 상태가 최종 상태로 저장된 뒤 Redis 정리는 재시도 작업이 담당합니다.
                }
            }
        });
    }

    public void suspendWaitingForProposalAfterCommit(MatchProposal proposal) {
        if (proposal == null || proposal.getExpiresAt() == null) {
            return;
        }
        Duration ttl = Duration.between(clock.instant(), proposal.getExpiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofMillis(1);
        }
        Duration proposalTtl = ttl;
        afterCommit(() -> {
            suspendWaiting(proposal.getRequest1(), proposalTtl);
            suspendWaiting(proposal.getRequest2(), proposalTtl);
        });
    }

    public boolean suspendWaiting(MatchRequest request, Duration proposalTtl) {
        if (request == null || request.getId() == null || request.getUser() == null
                || request.getUser().getId() == null || proposalTtl == null
                || proposalTtl.isZero() || proposalTtl.isNegative()) {
            return false;
        }
        try {
            return waitingStore.suspend(request.getUser().getId(), request.getId(), proposalTtl);
        } catch (DataAccessException ignored) {
            // Redis 장애 시 DB 상태를 되돌리지 않고 다음 보정 주기에서 재시도합니다.
            return false;
        }
    }

    public void restoreWaitingAfterCommit(MatchRequest request) {
        restoreWaitingAfterCommit(request, matchingProperties.waitingTtl());
    }

    public void restoreWaitingAfterCommit(MatchRequest request, Duration ttl) {
        if (request == null) {
            return;
        }
        afterCommit(() -> restoreWaiting(request, ttl));
    }

    public boolean restoreWaiting(MatchRequest request, Duration ttl) {
        if (request == null || request.getId() == null || request.getUser() == null
                || request.getUser().getId() == null || request.getLocation() == null
                || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        try {
            return waitingStore.restore(
                    request.getUser().getId(),
                    request.getId(),
                    request.getLocation().getX(),
                    request.getLocation().getY(),
                    ttl
            );
        } catch (DataAccessException ignored) {
            // Redis 장애 시 DB 상태를 되돌리지 않고 다음 복구 주기에서 재시도합니다.
            return false;
        }
    }

    public void restoreWaitingPairAfterCommit(MatchRequest first, MatchRequest second) {
        afterCommit(() -> restoreWaitingPair(first, second, matchingProperties.waitingTtl()));
    }

    public boolean restoreWaitingPair(MatchRequest first, MatchRequest second, Duration ttl) {
        if (!isRestorable(first, ttl) || !isRestorable(second, ttl)) {
            return false;
        }
        try {
            return waitingStore.restorePair(
                    entryOf(first),
                    entryOf(second),
                    ttl
            );
        } catch (DataAccessException ignored) {
            // Redis 장애 시 DB 상태를 되돌리지 않고 다음 복구 주기에서 재시도합니다.
            return false;
        }
    }

    public void putProposalAfterCommit(MatchProposal proposal) {
        if (proposal == null || proposal.getId() == null || proposal.getExpiresAt() == null) {
            return;
        }
        afterCommit(() -> {
            Duration ttl = Duration.between(clock.instant(), proposal.getExpiresAt());
            if (ttl.isZero() || ttl.isNegative()) {
                ttl = Duration.ofMillis(1);
            }
            try {
                proposalStore.put(proposal.getId(), ttl);
            } catch (DataAccessException ignored) {
                // 제안의 기준 상태는 DB에 있으며 Redis TTL은 보조 캐시입니다.
            }
        });
    }

    public void removeProposalAfterCommit(Long proposalId) {
        if (proposalId == null) {
            return;
        }
        afterCommit(() -> {
            try {
                proposalStore.remove(proposalId);
            } catch (DataAccessException ignored) {
                // 제안 종료 후의 보조 캐시 정리는 재시도 작업에 맡깁니다.
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private boolean isRestorable(MatchRequest request, Duration ttl) {
        return request != null && request.getId() != null && request.getUser() != null
                && request.getUser().getId() != null && request.getLocation() != null
                && ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private RealtimeMatchWaitingEntry entryOf(MatchRequest request) {
        return new RealtimeMatchWaitingEntry(
                request.getUser().getId(),
                request.getId(),
                request.getLocation().getX(),
                request.getLocation().getY()
        );
    }
}
