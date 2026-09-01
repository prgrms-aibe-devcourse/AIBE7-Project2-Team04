package org.example.project2.domain.personality.service.embedding;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 구버전 성향 임베딩을 새 자유 텍스트 문서 버전으로 점진적으로 재생성하도록 예약합니다.
 *
 * <p>실제 임베딩 호출은 기존 비동기 이벤트 핸들러가 수행합니다. 따라서 AI 장애가 발생해도
 * 기존 구버전 행을 새 버전으로 잘못 표시하지 않으며, 같은 배치를 다시 실행해 재시도할 수 있습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalityEmbeddingMigrationService {
    public static final int DEFAULT_BATCH_SIZE = 100;
    private static final int EMBEDDING_DIMENSIONS = 1536;

    private final UserPersonalityProfileRepository profileRepository;
    private final UserPersonalityEmbeddingRepository embeddingRepository;
    private final PersonalityAiClient aiClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 동의가 유지되고 자기소개가 있는 프로필 중 새 문서/모델로 준비되지 않은 건을 재생성 예약합니다.
     * 구버전 임베딩은 성공적으로 교체될 때까지 그대로 남지만 새 점수 계산의 호환성 검사에서 제외됩니다.
     */
    @Transactional
    public MigrationResult requeueStaleProfileEmbeddings() {
        return requeueStaleProfileEmbeddings(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public MigrationResult requeueStaleProfileEmbeddings(int batchSize) {
        int normalizedBatchSize = validateBatchSize(batchSize);
        String currentModel = normalize(aiClient.embeddingModelName());
        List<UserPersonalityProfile> profiles = profileRepository
                .findAllByAiAnalysisConsentTrueAndSelfDescriptionIsNotNull(
                        PageRequest.of(0, normalizedBatchSize, Sort.by(Sort.Direction.ASC, "userId"))
                );

        int queuedCount = 0;
        for (UserPersonalityProfile profile : profiles) {
            String sourceText = profile.getSelfDescription();
            if (sourceText == null || sourceText.isBlank()) {
                continue;
            }

            UUID userId = userIdOf(profile);
            if (userId == null) {
                continue;
            }
            UserPersonalityEmbedding existing = embeddingRepository.findAllByProfileUserId(userId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (isCurrent(existing, sourceText, currentModel)) {
                continue;
            }

            eventPublisher.publishEvent(new PersonalityEmbeddingRequestedEvent(
                    userId,
                    sourceText
            ));
            queuedCount++;
        }
        return new MigrationResult(profiles.size(), queuedCount);
    }

    private boolean isCurrent(
            UserPersonalityEmbedding embedding,
            String profileText,
            String currentModel
    ) {
        if (currentModel == null
                || embedding == null
                || !Objects.equals(normalize(embedding.getSourceText()), normalize(profileText))
                || !Objects.equals(normalize(embedding.getModelName()), currentModel)
                || !PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION
                .equals(embedding.getSourceVersion())) {
            return false;
        }

        float[] values = embedding.getEmbedding();
        if (values == null || values.length != EMBEDDING_DIMENSIONS) {
            return false;
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private UUID userIdOf(UserPersonalityProfile profile) {
        if (profile.getUserId() != null) {
            return profile.getUserId();
        }
        return profile.getUser() == null ? null : profile.getUser().getId();
    }

    private int validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("임베딩 재생성 배치 크기는 양수여야 합니다.");
        }
        return batchSize;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record MigrationResult(int scannedCount, int queuedCount) {
        public MigrationResult {
            if (scannedCount < 0 || queuedCount < 0 || queuedCount > scannedCount) {
                throw new IllegalArgumentException("임베딩 재생성 결과 건수가 올바르지 않습니다.");
            }
        }
    }
}
