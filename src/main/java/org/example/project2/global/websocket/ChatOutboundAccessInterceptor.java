package org.example.project2.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.entity.ChatRoomStatus;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Outbound 채널 인터셉터.
 * <ul>
 *   <li>MESSAGE 프레임이 클라이언트로 나가기 전에 채팅방이 CLOSED 상태이면 차단합니다.</li>
 * </ul>
 *
 * <p>SimpleBroker는 destination을 구독한 세션에만 전달하므로 수신자 재검증은 생략합니다.
 * 수신 권한 검증은 Inbound의 {@link ChatSubscriptionInterceptor}에서 구독 시점에 이미 처리합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOutboundAccessInterceptor implements ChannelInterceptor {

    private static final String TOPIC_CHAT_PREFIX = "/topic/chat/";

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // 서버→클라이언트 MESSAGE 타입만 처리
        if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
            String destination = accessor.getDestination();
            Long roomId = extractRoomId(destination);
            if (roomId != null) {
                validateRoomActive(roomId);
            }
        }

        return message;
    }

    // ── 헬퍼 메서드 ────────────────────────────────────────────────────────────

    /**
     * destination "/topic/chat/{roomId}"에서 roomId를 추출합니다.
     */
    private Long extractRoomId(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_CHAT_PREFIX)) {
            return null;
        }
        String segment = destination.substring(TOPIC_CHAT_PREFIX.length()).split("/")[0];
        try {
            return Long.parseLong(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 채팅방이 CLOSED 상태이면 메시지 전송을 차단합니다. */
    private void validateRoomActive(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room != null && ChatRoomStatus.CLOSED.equals(room.getStatus())) {
            log.warn("[WS OUTBOUND] 종료된 채팅방으로의 메시지 전송 차단. roomId={}", roomId);
            throw new MessagingException("종료된 채팅방에는 메시지를 전송할 수 없습니다.");
        }
    }
}

