package org.example.project2.domain.review.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 후기 운영 감사에 필요한 최소 정보만 담는 애플리케이션 이벤트입니다.
 *
 * <p>식별자 필드는 원본 UUID·숫자 ID가 아니라 {@link ReviewAuditPseudonymizer}가
 * 생성한 가명 키만 저장합니다. 후기 선택값, 인증 정보, 연락처와 위치 정보는
 * 이벤트에 포함하지 않습니다.</p>
 */
public record ReviewAuditEvent(
        Type type,
        Instant occurredAt,
        String keyVersion,
        String actorKey,
        String subjectKey,
        String matchKey,
        String reviewKey,
        String outcomeCode
) {
    public ReviewAuditEvent {
        type = Objects.requireNonNull(type, "후기 감사 이벤트 유형은 필수입니다.");
        occurredAt = Objects.requireNonNull(occurredAt, "후기 감사 이벤트 시각은 필수입니다.");
        keyVersion = requireText(keyVersion, "후기 감사 가명 키 버전은 필수입니다.");
        outcomeCode = requireText(outcomeCode, "후기 감사 결과 코드는 필수입니다.");
    }

    public enum Type {
        REVIEW_SUBMITTED,
        REVIEW_DUPLICATE_REJECTED,
        REVIEW_PERIOD_EXPIRED,
        REVIEW_REPORT_HANDLED
    }

    /** 신고 처리 결과는 자유 입력 대신 고정된 운영 코드만 사용합니다. */
    public enum ReportOutcome {
        ACCEPTED,
        REJECTED,
        DISMISSED,
        ESCALATED
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
