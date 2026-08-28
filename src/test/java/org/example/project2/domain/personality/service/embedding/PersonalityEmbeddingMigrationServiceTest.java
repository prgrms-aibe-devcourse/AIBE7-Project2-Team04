package org.example.project2.domain.personality.service.embedding;

import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalityEmbeddingMigrationServiceTest {
    @Mock UserPersonalityProfileRepository profileRepository;
    @Mock UserPersonalityEmbeddingRepository embeddingRepository;
    @Mock PersonalityAiClient aiClient;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void queuesOnlyEligibleProfilesWithoutCurrentModelAndTextEmbedding() {
        UUID staleUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UserPersonalityProfile staleProfile = profile(staleUserId, "  이전 형식 소개  ");
        UserPersonalityProfile currentProfile = profile(currentUserId, "현재 소개");
        UserPersonalityEmbedding staleEmbedding = embedding(
                "이전 형식 소개", "personality-document-v1:legacy", "embedding-model"
        );
        UserPersonalityEmbedding currentEmbedding = embedding(
                "현재 소개", PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION,
                "embedding-model"
        );
        when(currentEmbedding.getEmbedding()).thenReturn(new float[1536]);
        when(aiClient.embeddingModelName()).thenReturn("embedding-model");
        when(profileRepository.findAllByAiAnalysisConsentTrueAndSelfDescriptionIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(staleProfile, currentProfile));
        when(embeddingRepository.findById(staleUserId)).thenReturn(Optional.of(staleEmbedding));
        when(embeddingRepository.findById(currentUserId)).thenReturn(Optional.of(currentEmbedding));

        PersonalityEmbeddingMigrationService service = new PersonalityEmbeddingMigrationService(
                profileRepository, embeddingRepository, aiClient, eventPublisher
        );

        PersonalityEmbeddingMigrationService.MigrationResult result =
                service.requeueStaleProfileEmbeddings(2);

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.queuedCount()).isEqualTo(1);
        ArgumentCaptor<PersonalityEmbeddingRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PersonalityEmbeddingRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(staleUserId);
        assertThat(eventCaptor.getValue().sourceText()).isEqualTo(staleProfile.getSelfDescription());
    }

    @Test
    void requeuesMissingEmbeddingSoAFailedBatchCanBeRetried() {
        UUID userId = UUID.randomUUID();
        UserPersonalityProfile profile = profile(userId, "소개");
        when(aiClient.embeddingModelName()).thenReturn("embedding-model");
        when(profileRepository.findAllByAiAnalysisConsentTrueAndSelfDescriptionIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(profile));
        when(embeddingRepository.findById(userId)).thenReturn(Optional.empty());

        PersonalityEmbeddingMigrationService service = new PersonalityEmbeddingMigrationService(
                profileRepository, embeddingRepository, aiClient, eventPublisher
        );

        PersonalityEmbeddingMigrationService.MigrationResult result =
                service.requeueStaleProfileEmbeddings();

        assertThat(result.queuedCount()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new PersonalityEmbeddingRequestedEvent(userId, "소개"));
        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void rejectsNonPositiveProfileBatchSize() {
        PersonalityEmbeddingMigrationService service = new PersonalityEmbeddingMigrationService(
                profileRepository, embeddingRepository, aiClient, eventPublisher
        );

        assertThatThrownBy(() -> service.requeueStaleProfileEmbeddings(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("임베딩 재생성 배치 크기는 양수여야 합니다.");
    }

    private UserPersonalityProfile profile(UUID userId, String selfDescription) {
        UserPersonalityProfile profile = org.mockito.Mockito.mock(UserPersonalityProfile.class);
        when(profile.getUserId()).thenReturn(userId);
        when(profile.getSelfDescription()).thenReturn(selfDescription);
        return profile;
    }

    private UserPersonalityEmbedding embedding(
            String sourceText,
            String sourceVersion,
            String modelName
    ) {
        UserPersonalityEmbedding embedding = org.mockito.Mockito.mock(UserPersonalityEmbedding.class);
        when(embedding.getSourceText()).thenReturn(sourceText);
        when(embedding.getSourceVersion()).thenReturn(sourceVersion);
        when(embedding.getModelName()).thenReturn(modelName);
        return embedding;
    }
}
