package org.example.project2.domain.matching.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RealtimeMatchWaitingStore {
    boolean reserve(UUID userId, String reservationToken, Duration ttl);

    boolean activate(
            UUID userId,
            String reservationToken,
            long requestId,
            double longitude,
            double latitude,
            Duration ttl
    );

    boolean suspend(UUID userId, long requestId, Duration proposalTtl);

    boolean restore(UUID userId, long requestId, double longitude, double latitude, Duration ttl);

    boolean restorePair(
            RealtimeMatchWaitingEntry first,
            RealtimeMatchWaitingEntry second,
            Duration ttl
    );

    void releaseReservation(UUID userId, String reservationToken);

    void remove(UUID userId, long requestId);

    int cleanupExpiredGeoMembers();

    Optional<Duration> remainingTtl(long requestId);
}
