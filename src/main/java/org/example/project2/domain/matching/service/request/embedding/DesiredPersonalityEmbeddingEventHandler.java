package org.example.project2.domain.matching.service.request.embedding;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DesiredPersonalityEmbeddingEventHandler {
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final String SOURCE_VERSION = "DESIRED_PERSONALITY_TEXT_V1";

    private final MatchRequestRepository matchRequestRepository;
    private final PersonalityAiClient aiClient;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(DesiredPersonalityEmbeddingRequestedEvent event) {
        Optional<MatchRequest> optionalRequest = matchRequestRepository.findById(event.requestId());
        if (optionalRequest.isEmpty()) {
            return;
        }
        MatchRequest request = optionalRequest.get();
        if (!event.sourceText().equals(request.getDesiredPersonalityText())) {
            return;
        }
        Optional<float[]> optionalEmbedding = aiClient.embed(event.sourceText());
        if (optionalEmbedding.isEmpty() || optionalEmbedding.get().length != EMBEDDING_DIMENSIONS) {
            return;
        }
        request.updateDesiredPersonalityEmbedding(
                optionalEmbedding.get(),
                aiClient.embeddingModelName(),
                SOURCE_VERSION,
                Instant.now()
        );
    }
}
