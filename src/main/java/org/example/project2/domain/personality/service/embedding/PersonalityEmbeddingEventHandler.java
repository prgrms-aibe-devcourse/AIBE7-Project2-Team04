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
            embeddingRepository.deleteAllByProfileUserId(event.userId());
            return;
        }
        if (!event.sourceText().equals(profile.getSelfDescription())) {
            return;
        }

        java.util.List<String> keywords = aiClient.extractKeywords(event.sourceText()).orElse(java.util.List.of());

        // 외부 AI 호출 중 프로필이 수정·삭제될 수 있으므로 저장 직전에 다시 확인합니다.
        Optional<UserPersonalityProfile> latestProfile = profileRepository.findByUserId(event.userId());
        if (latestProfile.isEmpty()
                || !latestProfile.get().isAiAnalysisConsent()
                || !event.sourceText().equals(latestProfile.get().getSelfDescription())) {
            return;
        }

        UserPersonalityProfile targetProfile = latestProfile.get();
        if (!keywords.isEmpty()) {
            java.util.Set<org.example.project2.domain.personality.entity.PersonalityTag> preservedTags =
                    targetProfile.getStyleTags() != null
                            ? new java.util.HashSet<>(targetProfile.getStyleTags())
                            : java.util.Set.of();
            targetProfile.replace(
                    targetProfile.getQuestionnaireVersion(),
                    targetProfile.getConversationLevel(),
                    targetProfile.getMealPace(),
                    targetProfile.getPlanningStyle(),
                    targetProfile.getNoveltyPreference(),
                    preservedTags,
                    targetProfile.getSelfDescription(),
                    targetProfile.isAiAnalysisConsent(),
                    targetProfile.getCompletedAt(),
                    keywords
            );
            profileRepository.saveAndFlush(targetProfile);
        }

        // 1NF 원자화: 기존 해당 유저의 임베딩 레코드 전체 삭제 후 단어별 1:1 레코드 독립 INSERT
        embeddingRepository.deleteAllByProfileUserId(event.userId());

        java.util.List<UserPersonalityEmbedding> newEmbeddings = new java.util.ArrayList<>();
        java.time.Instant now = java.time.Instant.now();

        if (!keywords.isEmpty()) {
            for (String word : keywords) {
                if (word == null || word.isBlank()) continue;
                String cleanWord = word.strip();
                Optional<float[]> wordEmb = aiClient.embed(cleanWord);
                if (wordEmb.isPresent() && wordEmb.get().length == EMBEDDING_DIMENSIONS) {
                    newEmbeddings.add(UserPersonalityEmbedding.builder()
                            .profile(targetProfile)
                            .sourceText(cleanWord)
                            .embedding(wordEmb.get())
                            .modelName(aiClient.embeddingModelName())
                            .sourceVersion(PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION)
                            .generatedAt(now)
                            .build());
                }
            }
        } else {
            Optional<float[]> rawEmb = aiClient.embed(event.sourceText());
            if (rawEmb.isPresent() && rawEmb.get().length == EMBEDDING_DIMENSIONS) {
                newEmbeddings.add(UserPersonalityEmbedding.builder()
                        .profile(targetProfile)
                        .sourceText(event.sourceText().strip())
                        .embedding(rawEmb.get())
                        .modelName(aiClient.embeddingModelName())
                        .sourceVersion(PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION)
                        .generatedAt(now)
                        .build());
            }
        }

        if (!newEmbeddings.isEmpty()) {
            embeddingRepository.saveAll(newEmbeddings);
        }
    }
}
