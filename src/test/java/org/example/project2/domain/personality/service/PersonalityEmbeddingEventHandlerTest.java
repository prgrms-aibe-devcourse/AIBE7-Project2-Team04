package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingDocument;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingDocumentBuilder;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingEventHandler;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingRequestedEvent;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalityEmbeddingEventHandlerTest {
    @Mock UserPersonalityProfileRepository profileRepository;
    @Mock UserPersonalityEmbeddingRepository embeddingRepository;
    @Mock
    PersonalityAiClient aiClient;

    @Test
    void savesVersioned1536DimensionEmbedding() {
        UUID userId = UUID.randomUUID();
        UserPersonalityProfile profile = profile(userId, true, "새로운 맛집을 좋아해요.");
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(aiClient.embed(any())).thenReturn(Optional.of(new float[1536]));
        when(aiClient.embeddingModelName()).thenReturn("gemini-embedding-001");
        when(embeddingRepository.findById(userId)).thenReturn(Optional.empty());
        PersonalityEmbeddingEventHandler handler = new PersonalityEmbeddingEventHandler(
                profileRepository, embeddingRepository, new PersonalityEmbeddingDocumentBuilder(), aiClient
        );

        handler.generate(new PersonalityEmbeddingRequestedEvent(userId));

        verify(embeddingRepository).save(any(UserPersonalityEmbedding.class));
    }

    @Test
    void ignoresAiFailureAndDoesNotSaveEmbedding() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profile(userId, true, "소개")));
        when(aiClient.embed(any())).thenReturn(Optional.empty());
        PersonalityEmbeddingEventHandler handler = new PersonalityEmbeddingEventHandler(
                profileRepository, embeddingRepository, new PersonalityEmbeddingDocumentBuilder(), aiClient
        );

        handler.generate(new PersonalityEmbeddingRequestedEvent(userId));

        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void documentIsDeterministicAndVersioned() {
        PersonalityEmbeddingDocumentBuilder builder = new PersonalityEmbeddingDocumentBuilder();
        UserPersonalityProfile profile = profile(UUID.randomUUID(), true, "소개");

        PersonalityEmbeddingDocument first = builder.build(profile);
        PersonalityEmbeddingDocument second = builder.build(profile);

        assertThat(first).isEqualTo(second);
        assertThat(first.sourceVersion()).startsWith("personality-document-v1:");
        assertThat(first.sourceText()).contains("confirmedTags=GOOD_LISTENER", "selfDescription=소개");
    }

    private UserPersonalityProfile profile(UUID userId, boolean consent, String description) {
        User user = User.builder().id(userId).email("user@test.com")
                .passwordHash("hash").nickname("user").build();
        return UserPersonalityProfile.builder()
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 50)
                .mealPace((short) 50)
                .planningStyle((short) 50)
                .noveltyPreference((short) 50)
                .styleTags(new HashSet<>(Set.of(PersonalityTag.GOOD_LISTENER)))
                .selfDescription(description)
                .aiAnalysisConsent(consent)
                .build();
    }
}
