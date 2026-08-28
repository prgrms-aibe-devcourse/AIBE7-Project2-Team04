package org.example.project2.domain.matching.repository;

import java.util.UUID;

public record RealtimeMatchWaitingEntry(
        UUID userId,
        long requestId,
        double longitude,
        double latitude
) {
}
