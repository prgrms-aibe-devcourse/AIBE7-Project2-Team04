package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 인증된 사용자별 STOMP 목적지로 매칭 제안 알림을 전달합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchProposalWebSocketPublisher {
    public static final String DESTINATION = "/queue/match-proposal";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(MatchProposalCreatedEvent event) {
        send(event.request1UserId(), event.request1Payload());
        send(event.request2UserId(), event.request2Payload());
    }

    private void send(UUID userId, MatchProposalResponse payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    DESTINATION,
                    payload
            );
        } catch (RuntimeException ignored) {
            // 한쪽 세션이 끊겨도 다른 사용자에게 전달을 시도하고, 민감한 프로필 내용은 로그에 남기지 않습니다.
            log.warn("매칭 제안 WebSocket 알림 전송에 실패했습니다. errorCode=MATCHING_NOTIFICATION_FAILED");
        }
    }
}
