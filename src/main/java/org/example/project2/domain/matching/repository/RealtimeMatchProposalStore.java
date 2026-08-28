package org.example.project2.domain.matching.repository;

import java.time.Duration;
import java.util.Optional;

public interface RealtimeMatchProposalStore {
    void put(long proposalId, Duration ttl);

    void remove(long proposalId);

    Optional<Duration> remainingTtl(long proposalId);
}
