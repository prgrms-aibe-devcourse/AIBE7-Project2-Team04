package org.example.project2.domain.matching.service.request.embedding;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.embedding.PersonalityTextEmbeddingDocumentBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesiredPersonalityEmbeddingMigrationServiceTest {
    @Mock MatchRequestRepository matchRequestRepository;
    @Mock PersonalityAiClient aiClient;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void clearsAndQueuesLegacyActiveRequestWhileLeavingCurrentRequestUntouched() {
        MatchRequest legacyRequest = request(55L, "편안한 대화를 원해요.");
        MatchRequest currentRequest = request(56L, "새로운 메뉴를 원해요.");
        when(legacyRequest.getId()).thenReturn(55L);
        when(legacyRequest.getDesiredPersonalityEmbedding()).thenReturn(new float[1536]);
        when(legacyRequest.getEmbeddingModel()).thenReturn("embedding-model");
        when(legacyRequest.getEmbeddingVersion()).thenReturn("personality-document-v1:legacy");
        when(currentRequest.getDesiredPersonalityEmbedding()).thenReturn(new float[1536]);
        when(currentRequest.getEmbeddingModel()).thenReturn("embedding-model");
        when(currentRequest.getEmbeddingVersion())
                .thenReturn(PersonalityTextEmbeddingDocumentBuilder.DOCUMENT_VERSION);
        when(currentRequest.getEmbeddedAt()).thenReturn(Instant.now());
        when(aiClient.embeddingModelName()).thenReturn("embedding-model");
        when(matchRequestRepository.findAllByStatusInAndDesiredPersonalityTextIsNotNull(
                any(), any(Pageable.class)
        )).thenReturn(List.of(legacyRequest, currentRequest));

        DesiredPersonalityEmbeddingMigrationService service =
                new DesiredPersonalityEmbeddingMigrationService(
                        matchRequestRepository, aiClient, eventPublisher
                );

        DesiredPersonalityEmbeddingMigrationService.MigrationResult result =
                service.requeueStaleActiveRequestEmbeddings(2);

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.queuedCount()).isEqualTo(1);
        assertThat(result.clearedCount()).isEqualTo(1);
        verify(legacyRequest).clearDesiredPersonalityEmbedding();
        verify(currentRequest, never()).clearDesiredPersonalityEmbedding();

        ArgumentCaptor<DesiredPersonalityEmbeddingRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DesiredPersonalityEmbeddingRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().requestId()).isEqualTo(55L);
        assertThat(eventCaptor.getValue().sourceText()).isEqualTo("편안한 대화를 원해요.");
    }

    @Test
    void leavesFailedRequestWithoutEmbeddingReadyForARepeatableRetry() {
        MatchRequest request = request(55L, "편안한 대화를 원해요.");
        when(request.getId()).thenReturn(55L);
        when(request.getDesiredPersonalityEmbedding()).thenReturn(null);
        when(request.getEmbeddingModel()).thenReturn(null);
        when(request.getEmbeddingVersion()).thenReturn(null);
        when(request.getEmbeddedAt()).thenReturn(null);
        when(aiClient.embeddingModelName()).thenReturn("embedding-model");
        when(matchRequestRepository.findAllByStatusInAndDesiredPersonalityTextIsNotNull(
                any(), any(Pageable.class)
        )).thenReturn(List.of(request));

        DesiredPersonalityEmbeddingMigrationService service =
                new DesiredPersonalityEmbeddingMigrationService(
                        matchRequestRepository, aiClient, eventPublisher
                );

        DesiredPersonalityEmbeddingMigrationService.MigrationResult result =
                service.requeueStaleActiveRequestEmbeddings();

        assertThat(result.queuedCount()).isEqualTo(1);
        assertThat(result.clearedCount()).isZero();
        verify(eventPublisher).publishEvent(
                new DesiredPersonalityEmbeddingRequestedEvent(55L, "편안한 대화를 원해요.")
        );
        verify(request, never()).clearDesiredPersonalityEmbedding();
    }

    @Test
    void rejectsNonPositiveRequestBatchSize() {
        DesiredPersonalityEmbeddingMigrationService service =
                new DesiredPersonalityEmbeddingMigrationService(
                        matchRequestRepository, aiClient, eventPublisher
                );

        assertThatThrownBy(() -> service.requeueStaleActiveRequestEmbeddings(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("임베딩 재생성 배치 크기는 양수여야 합니다.");
    }

    private MatchRequest request(Long requestId, String text) {
        MatchRequest request = org.mockito.Mockito.mock(MatchRequest.class);
        when(request.getDesiredPersonalityText()).thenReturn(text);
        return request;
    }
}
