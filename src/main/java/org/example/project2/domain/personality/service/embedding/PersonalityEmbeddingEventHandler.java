package org.example.project2.domain.personality.service.embedding;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PersonalityEmbeddingEventHandler {
    private static final int EMBEDDING_DIMENSIONS = 1536;

    private final UserPersonalityProfileRepository profileRepository;
    private final UserPersonalityEmbeddingRepository embeddingRepository;
    private final PersonalityTextEmbeddingDocumentBuilder documentBuilder;
    private final PersonalityAiClient aiClient;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(PersonalityEmbeddingRequestedEvent event) {
        if (event == null || event.userId() == null || event.sourceText() == null) {
            return;
        }
        Optional<UserPersonalityProfile> optionalProfile = profileRepository.findByUserId(event.userId());
        if (optionalProfile.isEmpty()) {
            return;
        }
        UserPersonalityProfile profile = optionalProfile.get();
        if (!profile.isAiAnalysisConsent() || profile.getSelfDescription() == null) {
            embeddingRepository.deleteById(event.userId());
            return;
        }
        if (!event.sourceText().equals(profile.getSelfDescription())) {
            return;
        }

        PersonalityEmbeddingDocument document = documentBuilder.build(event.sourceText());
        Optional<float[]> optionalEmbedding = aiClient.embed(document.sourceText());
        if (optionalEmbedding.isEmpty() || optionalEmbedding.get().length != EMBEDDING_DIMENSIONS) {
            return;
        }

        // 외부 AI 호출 중 프로필이 수정·삭제될 수 있으므로 저장 직전에 다시 확인합니다.
        Optional<UserPersonalityProfile> latestProfile = profileRepository.findByUserId(event.userId());
        if (latestProfile.isEmpty()
                || !latestProfile.get().isAiAnalysisConsent()
                || !event.sourceText().equals(latestProfile.get().getSelfDescription())) {
            return;
        }

        float[] vector = optionalEmbedding.get();
        UserPersonalityEmbedding embedding = embeddingRepository.findById(event.userId())
                .orElseGet(() -> UserPersonalityEmbedding.builder()
                        .profile(profile)
                        .build());
        embedding.replace(
                document.sourceText(),
                vector,
                aiClient.embeddingModelName(),
                document.sourceVersion()
        );
        embeddingRepository.save(embedding);
    }
}
