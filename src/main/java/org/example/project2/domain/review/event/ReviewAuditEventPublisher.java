package org.example.project2.domain.review.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 후기 감사 이벤트를 발행합니다.
 *
 * <p>저장 성공 이벤트는 DB 커밋 이후에만 발행하고, 중복·기간 만료처럼
 * 거부된 시도는 트랜잭션 롤백과 관계없이 운영 감사에 남깁니다.</p>
 */
@Component
@RequiredArgsConstructor
public class ReviewAuditEventPublisher {
    private final ApplicationEventPublisher eventPublisher;
    private final ReviewAuditPseudonymizer pseudonymizer;
    private final Clock clock;

    public void reviewSubmitted(
            Long reviewId,
            Long matchId,
            UUID reviewerId,
            UUID revieweeId,
            Instant occurredAt
    ) {
        publishAfterCommit(event(
                ReviewAuditEvent.Type.REVIEW_SUBMITTED,
                occurredAt == null ? clock.instant() : occurredAt,
                reviewerId,
                revieweeId,
                matchId,
                reviewId,
                "SUCCESS"
        ));
    }

    public void duplicateRejected(Long matchId, UUID reviewerId, UUID revieweeId) {
        publishImmediately(event(
                ReviewAuditEvent.Type.REVIEW_DUPLICATE_REJECTED,
                clock.instant(),
                reviewerId,
                revieweeId,
                matchId,
                null,
                "REVIEW_ALREADY_SUBMITTED"
        ));
    }

    public void periodExpired(Long matchId, UUID reviewerId, UUID revieweeId) {
        publishImmediately(event(
                ReviewAuditEvent.Type.REVIEW_PERIOD_EXPIRED,
                clock.instant(),
                reviewerId,
                revieweeId,
                matchId,
                null,
                "REVIEW_PERIOD_EXPIRED"
        ));
    }

    /**
     * 신고 처리 기능이 연결될 때 사용할 감사 이벤트 발행 지점입니다.
     * 신고 원문이나 처리자의 개인정보 대신 고정된 처리 결과 코드만 받습니다.
     */
    public void reportHandled(
            Long reviewId,
            Long matchId,
            UUID operatorId,
            UUID revieweeId,
            ReviewAuditEvent.ReportOutcome outcome
    ) {
        if (outcome == null) {
            throw new IllegalArgumentException("후기 신고 처리 결과는 필수입니다.");
        }
        publishAfterCommit(event(
                ReviewAuditEvent.Type.REVIEW_REPORT_HANDLED,
                clock.instant(),
                operatorId,
                revieweeId,
                matchId,
                reviewId,
                "REPORT_" + outcome.name()
        ));
    }

    private ReviewAuditEvent event(
            ReviewAuditEvent.Type type,
            Instant occurredAt,
            UUID actorId,
            UUID subjectId,
            Long matchId,
            Long reviewId,
            String outcomeCode
    ) {
        return new ReviewAuditEvent(
                type,
                occurredAt,
                pseudonymizer.keyVersion(),
                pseudonymizer.userKey(actorId),
                pseudonymizer.userKey(subjectId),
                pseudonymizer.matchKey(matchId),
                pseudonymizer.reviewKey(reviewId),
                outcomeCode
        );
    }

    private void publishImmediately(ReviewAuditEvent event) {
        eventPublisher.publishEvent(event);
    }

    private void publishAfterCommit(ReviewAuditEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }
}
