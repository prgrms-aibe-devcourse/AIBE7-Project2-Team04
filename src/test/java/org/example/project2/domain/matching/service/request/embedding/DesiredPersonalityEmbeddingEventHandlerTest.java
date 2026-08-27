package org.example.project2.domain.matching.service.request.embedding;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                new DesiredPersonalityEmbeddingEventHandler(repository, aiClient);

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(
                55L,
                "편안하게 대화하는 분"
        ));

        verify(request).updateDesiredPersonalityEmbedding(
                eq(vector),
                eq("embedding-model"),
                eq("DESIRED_PERSONALITY_TEXT_V1"),
                any(Instant.class)
        );
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
                new DesiredPersonalityEmbeddingEventHandler(repository, aiClient);

        handler.generate(new DesiredPersonalityEmbeddingRequestedEvent(
                55L,
                "편안하게 대화하는 분"
        ));

        verify(request, never()).updateDesiredPersonalityEmbedding(any(), any(), any(), any());
    }
}
