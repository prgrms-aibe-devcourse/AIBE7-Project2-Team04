package org.example.project2.domain.review.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 개인정보가 제거된 후기 감사 이벤트를 운영 로그로 전달합니다.
 * 일반 애플리케이션 로그와 구분할 수 있도록 전용 로거 이름을 사용합니다.
 */
@Component
public class ReviewAuditEventHandler {
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.review");

    @EventListener
    public void handle(ReviewAuditEvent event) {
        if (event == null) {
            return;
        }

        AUDIT_LOG.info(
                "후기 감사 이벤트 eventType={}, outcomeCode={}, keyVersion={}, actorKey={}, "
                        + "subjectKey={}, matchKey={}, reviewKey={}, occurredAt={}",
                event.type(),
                event.outcomeCode(),
                event.keyVersion(),
                event.actorKey(),
                event.subjectKey(),
                event.matchKey(),
                event.reviewKey(),
                event.occurredAt()
        );
    }
}
