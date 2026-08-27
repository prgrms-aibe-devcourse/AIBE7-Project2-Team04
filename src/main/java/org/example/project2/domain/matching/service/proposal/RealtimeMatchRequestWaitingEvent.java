package org.example.project2.domain.matching.service.proposal;

import java.util.UUID;

public record RealtimeMatchRequestWaitingEvent(UUID userId, Long requestId) {
}
