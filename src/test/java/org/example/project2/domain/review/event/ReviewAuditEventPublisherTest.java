package org.example.project2.domain.review.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReviewAuditEventPublisherTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UUID REVIEWER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REVIEWEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock ApplicationEventPublisher eventPublisher;

    private ReviewAuditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReviewAuditEventPublisher(
                eventPublisher,
                new ReviewAuditPseudonymizer("a".repeat(32), "v1"),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void publishesRejectedSubmissionImmediatelyWithOnlySafeFields() {
        publisher.duplicateRejected(301L, REVIEWER_ID, REVIEWEE_ID);

        ArgumentCaptor<ReviewAuditEvent> captor = ArgumentCaptor.forClass(ReviewAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ReviewAuditEvent event = captor.getValue();

        assertThat(event.type()).isEqualTo(ReviewAuditEvent.Type.REVIEW_DUPLICATE_REJECTED);
        assertThat(event.outcomeCode()).isEqualTo("REVIEW_ALREADY_SUBMITTED");
        assertThat(event.keyVersion()).isEqualTo("v1");
        assertThat(event.actorKey()).doesNotContain(REVIEWER_ID.toString());
        assertThat(event.subjectKey()).doesNotContain(REVIEWEE_ID.toString());
        assertThat(event.toString()).doesNotContain(REVIEWER_ID.toString(), REVIEWEE_ID.toString());
    }

    @Test
    void recordsReportHandlingWithFixedOutcomeCode() {
        publisher.reportHandled(
                901L,
                301L,
                REVIEWER_ID,
                REVIEWEE_ID,
                ReviewAuditEvent.ReportOutcome.ACCEPTED
        );

        ArgumentCaptor<ReviewAuditEvent> captor = ArgumentCaptor.forClass(ReviewAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ReviewAuditEvent event = captor.getValue();

        assertThat(event.type()).isEqualTo(ReviewAuditEvent.Type.REVIEW_REPORT_HANDLED);
        assertThat(event.outcomeCode()).isEqualTo("REPORT_ACCEPTED");
        assertThat(event.reviewKey()).isNotBlank();
        assertThat(event.toString()).doesNotContain(REVIEWER_ID.toString(), REVIEWEE_ID.toString());
    }

    @Test
    void publishesSuccessfulSubmissionOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.reviewSubmitted(901L, 301L, REVIEWER_ID, REVIEWEE_ID, NOW);
            verifyNoInteractions(eventPublisher);

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCommit()
            );

            verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(ReviewAuditEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void doesNotPublishSuccessfulSubmissionAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.reviewSubmitted(901L, 301L, REVIEWER_ID, REVIEWEE_ID, NOW);

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );

            verifyNoInteractions(eventPublisher);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
