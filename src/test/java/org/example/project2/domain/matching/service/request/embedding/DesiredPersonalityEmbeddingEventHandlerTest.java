package org.example.project2.domain.matching.service.request.embedding;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.embedding.PersonalityTextEmbeddingDocumentBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DesiredPersonalityEmbeddingEventHandlerTest {

    @Test
    void storesGeneratedEmbeddingForUnchangedDesiredText() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        MatchRequest request = mock(MatchRequest.class);
        float[] vector = new float[1536];
        when(repository.findById(55L)).thenReturn(Optional.of(request));
        when(request.getDesiredPersonalityText()).thenReturn("편안하게 대화하는 분");
        when(aiClient.embed("편안하게 대화하는 분")).thenReturn(Optional.of(vector));
        when(aiClient.embeddingModelName()).thenReturn("embedding-model");
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(
                55L,
                "편안하게 대화하는 분"
        ));

        verify(request).updateDesiredPersonalityEmbedding(
                eq(vector),
                eq("embedding-model"),
                eq("PERSONALITY_FREE_TEXT_V2"),
                any(Instant.class)
        );
        verify(aiClient).embed("편안하게 대화하는 분");
    }

    @Test
    void ignoresAiFailureWithoutChangingRequest() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        MatchRequest request = mock(MatchRequest.class);
        when(repository.findById(55L)).thenReturn(Optional.of(request));
        when(request.getDesiredPersonalityText()).thenReturn("편안하게 대화하는 분");
        when(aiClient.embed("편안하게 대화하는 분")).thenReturn(Optional.empty());
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(
                55L,
                "편안하게 대화하는 분"
        ));

        verify(request, never()).updateDesiredPersonalityEmbedding(any(), any(), any(), any());
    }

    @Test
    void ignoresEventWhenDesiredTextChangedBeforeEmbedding() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        MatchRequest request = mock(MatchRequest.class);
        when(repository.findById(55L)).thenReturn(Optional.of(request));
        when(request.getDesiredPersonalityText()).thenReturn("새로운 희망 설명");
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(55L, "이전 희망 설명"));

        verifyNoInteractions(aiClient);
        verify(request, never()).updateDesiredPersonalityEmbedding(any(), any(), any(), any());
    }

    @Test
    void doesNotSaveEmbeddingWhenRequestIsDeletedDuringAiCall() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        MatchRequest request = mock(MatchRequest.class);
        when(repository.findById(55L)).thenReturn(Optional.of(request), Optional.empty());
        when(request.getDesiredPersonalityText()).thenReturn("편안하게 대화하는 분");
        when(aiClient.embed("편안하게 대화하는 분")).thenReturn(Optional.of(new float[1536]));
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(
                55L,
                "편안하게 대화하는 분"
        ));

        verify(request, never()).updateDesiredPersonalityEmbedding(any(), any(), any(), any());
    }

    @Test
    void doesNotSaveEmbeddingWhenDesiredTextChangesDuringAiCall() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        MatchRequest request = mock(MatchRequest.class);
        MatchRequest latestRequest = mock(MatchRequest.class);
        when(repository.findById(55L)).thenReturn(Optional.of(request), Optional.of(latestRequest));
        when(request.getDesiredPersonalityText()).thenReturn("이전 희망 설명");
        when(latestRequest.getDesiredPersonalityText()).thenReturn("새로운 희망 설명");
        when(aiClient.embed("이전 희망 설명")).thenReturn(Optional.of(new float[1536]));
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(55L, "이전 희망 설명"));

        verify(latestRequest, never()).updateDesiredPersonalityEmbedding(any(), any(), any(), any());
    }

    @Test
    void doesNotCallAiForBlankDesiredTextEvent() {
        MatchRequestRepository repository = mock(MatchRequestRepository.class);
        PersonalityAiClient aiClient = mock(PersonalityAiClient.class);
        DesiredPersonalityEmbeddingEventHandler handler =
                new DesiredPersonalityEmbeddingEventHandler(
                        repository, aiClient, new PersonalityTextEmbeddingDocumentBuilder()
                );

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(55L, "   "));

        verifyNoInteractions(repository, aiClient);
    }
}
