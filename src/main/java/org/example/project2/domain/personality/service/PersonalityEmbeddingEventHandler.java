package org.example.project2.domain.personality.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
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
    private final PersonalityEmbeddingDocumentBuilder documentBuilder;
    private final PersonalityAiClient aiClient;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(PersonalityEmbeddingRequestedEvent event) {
        Optional<UserPersonalityProfile> optionalProfile = profileRepository.findByUserId(event.userId());
        if (optionalProfile.isEmpty()) {
            return;
        }
        UserPersonalityProfile profile = optionalProfile.get();
        if (!profile.isAiAnalysisConsent() || profile.getSelfDescription() == null) {
            embeddingRepository.deleteById(event.userId());
            return;
        }

        PersonalityEmbeddingDocument document = documentBuilder.build(profile);
        Optional<float[]> optionalEmbedding = aiClient.embed(document.sourceText());
        if (optionalEmbedding.isEmpty() || optionalEmbedding.get().length != EMBEDDING_DIMENSIONS) {
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
