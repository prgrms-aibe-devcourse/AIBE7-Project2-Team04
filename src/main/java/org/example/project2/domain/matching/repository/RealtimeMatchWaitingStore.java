package org.example.project2.domain.matching.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RealtimeMatchWaitingStore {
    boolean reserve(UUID userId, String reservationToken, Duration ttl);

    boolean activate(UUID userId, String reservationToken, long requestId, Duration ttl);

    void releaseReservation(UUID userId, String reservationToken);

    void remove(UUID userId, long requestId);

    Optional<Duration> remainingTtl(long requestId);
}
