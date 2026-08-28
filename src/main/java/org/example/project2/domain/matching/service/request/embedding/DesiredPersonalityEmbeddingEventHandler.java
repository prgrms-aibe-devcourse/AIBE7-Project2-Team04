package org.example.project2.domain.matching.service.request.embedding;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingDocument;
import org.example.project2.domain.personality.service.embedding.PersonalityTextEmbeddingDocumentBuilder;
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

    private final MatchRequestRepository matchRequestRepository;
    private final PersonalityAiClient aiClient;
    private final PersonalityTextEmbeddingDocumentBuilder documentBuilder;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(DesiredPersonalityEmbeddingRequestedEvent event) {
        if (event == null || event.requestId() == null
                || event.sourceText() == null || event.sourceText().isBlank()) {
            return;
        }
        Optional<MatchRequest> optionalRequest = matchRequestRepository.findById(event.requestId());
        if (optionalRequest.isEmpty()) {
            return;
        }
        MatchRequest request = optionalRequest.get();
        if (!event.sourceText().equals(request.getDesiredPersonalityText())) {
            return;
        }
        PersonalityEmbeddingDocument document = documentBuilder.build(event.sourceText());
        Optional<float[]> optionalEmbedding = aiClient.embed(document.sourceText());
        if (optionalEmbedding.isEmpty() || optionalEmbedding.get().length != EMBEDDING_DIMENSIONS) {
            return;
        }

        // 외부 AI 호출 중 요청이 삭제·변경될 수 있으므로 저장 직전에 다시 확인합니다.
        Optional<MatchRequest> latestRequest = matchRequestRepository.findById(event.requestId());
        if (latestRequest.isEmpty()
                || !event.sourceText().equals(latestRequest.get().getDesiredPersonalityText())) {
            return;
        }
        latestRequest.get().updateDesiredPersonalityEmbedding(
                optionalEmbedding.get(),
                aiClient.embeddingModelName(),
                document.sourceVersion(),
                Instant.now()
        );
    }
}
