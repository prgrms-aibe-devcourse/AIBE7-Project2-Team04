package org.example.project2.domain.matching.service.proposal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 인증된 사용자별 STOMP 목적지로 최종 매칭 결과를 전달합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchResultWebSocketPublisher {
    public static final String DESTINATION = "/queue/match-result";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(MatchResultCreatedEvent event) {
        send(event.request1UserId(), event.request1Payload());
        send(event.request2UserId(), event.request2Payload());
    }

    private void send(UUID userId, MatchResultResponse payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    DESTINATION,
                    payload
            );
        } catch (RuntimeException exception) {
            // 결과 전송 실패가 이미 커밋된 매칭을 되돌리지는 않습니다.
            log.warn("매칭 결과 WebSocket 알림 전송에 실패했습니다. matchId={}, userId={}",
                    payload.matchId(), userId, exception);
        }
    }
}
