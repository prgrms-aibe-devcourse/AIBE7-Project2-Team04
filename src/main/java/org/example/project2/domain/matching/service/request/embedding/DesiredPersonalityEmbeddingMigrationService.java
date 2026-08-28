package org.example.project2.domain.matching.service.request.embedding;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.embedding.PersonalityTextEmbeddingDocumentBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 활성 매칭 요청의 희망 상대 임베딩을 새 자유 텍스트 버전으로 점진적으로 재생성하도록 예약합니다.
 * 구버전 벡터는 즉시 제거하여 재생성 중에는 태그 점수로 안전하게 fallback합니다.
 */
@Service
@RequiredArgsConstructor
public class DesiredPersonalityEmbeddingMigrationService {
    public static final int DEFAULT_BATCH_SIZE = 100;
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final List<MatchRequestStatus> ACTIVE_STATUSES =
            List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING);

    private final MatchRequestRepository matchRequestRepository;
    private final PersonalityAiClient aiClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 활성 요청 중 새 문서/모델로 준비되지 않은 희망 텍스트 임베딩을 재생성 예약합니다.
     */
    @Transactional
    public MigrationResult requeueStaleActiveRequestEmbeddings() {
        return requeueStaleActiveRequestEmbeddings(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public MigrationResult requeueStaleActiveRequestEmbeddings(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("임베딩 재생성 배치 크기는 양수여야 합니다.");
        }

        String currentModel = normalize(aiClient.embeddingModelName());
        List<MatchRequest> requests = matchRequestRepository
                .findAllByStatusInAndDesiredPersonalityTextIsNotNull(
                        ACTIVE_STATUSES,
                        PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id"))
                );

        int queuedCount = 0;
        int clearedCount = 0;
        for (MatchRequest request : requests) {
            String sourceText = request.getDesiredPersonalityText();
            if (sourceText == null || sourceText.isBlank()) {
                continue;
            }
            if (isCurrent(request, currentModel)) {
                continue;
            }

            if (hasAnyEmbeddingMetadata(request)) {
                request.clearDesiredPersonalityEmbedding();
                clearedCount++;
            }
            eventPublisher.publishEvent(new DesiredPersonalityEmbeddingRequestedEvent(
                    request.getId(),
                    sourceText
            ));
            queuedCount++;
        }
        return new MigrationResult(requests.size(), queuedCount, clearedCount);
    }

    private boolean isCurrent(MatchRequest request, String currentModel) {
        if (currentModel == null
                || !Objects.equals(normalize(request.getEmbeddingModel()), currentModel)
                || !PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION
                .equals(request.getEmbeddingVersion())) {
            return false;
        }

        float[] values = request.getDesiredPersonalityEmbedding();
        if (values == null || values.length != EMBEDDING_DIMENSIONS
                || request.getEmbeddedAt() == null) {
            return false;
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAnyEmbeddingMetadata(MatchRequest request) {
        return request.getDesiredPersonalityEmbedding() != null
                || request.getEmbeddingModel() != null
                || request.getEmbeddingVersion() != null
                || request.getEmbeddedAt() != null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record MigrationResult(int scannedCount, int queuedCount, int clearedCount) {
        public MigrationResult {
            if (scannedCount < 0 || queuedCount < 0 || clearedCount < 0
                    || queuedCount > scannedCount || clearedCount > queuedCount) {
                throw new IllegalArgumentException("임베딩 재생성 결과 건수가 올바르지 않습니다.");
            }
        }
    }
}
