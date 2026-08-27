package org.example.project2.domain.matching.service.request.embedding;

public record DesiredPersonalityEmbeddingRequestedEvent(
        Long requestId,
        String sourceText
) {
}
