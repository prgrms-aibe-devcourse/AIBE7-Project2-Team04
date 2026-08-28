package org.example.project2.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound 채널 인터셉터.
 * <ul>
 *   <li>CONNECT  : {@link WebSocketHandshakeInterceptor}가 HTTP 핸드셰이크 시 attributes에 저장한
 *                  userId를 꺼내 STOMP session principal로 설정합니다.</li>
 *   <li>SUBSCRIBE: destination에서 roomId를 추출해 채팅방 참여자인지 검증합니다.</li>
 *   <li>SEND     : destination에서 roomId를 추출해 채팅방 참여자인지 검증합니다.</li>
 * </ul>
 *
 * <p>JWT 파싱은 핸드셰이크 단계에서 이미 완료되었으므로 이 인터셉터에서는 토큰을 다시 검증하지 않습니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private static final String SUBSCRIBE_PREFIX = "/topic/chat/";
    private static final String SEND_PREFIX = "/app/chat/";
    private static final String MATCH_PROPOSAL_DESTINATION = "/user/queue/match-proposal";
    private static final String MATCH_RESULT_DESTINATION = "/user/queue/match-result";

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            // 핸드셰이크 시 저장된 userId를 꺼내 STOMP principal로 설정
            UUID userId = getUserIdFromAttributes(accessor);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null);
            accessor.setUser(auth);
            log.debug("[WS CONNECT] userId={}", userId);

        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            UUID userId = getPrincipalId(accessor);
            String destination = accessor.getDestination();
            if (isMatchUserDestination(destination)) {
                log.debug("[WS SUBSCRIBE] 개인 매칭 큐 구독. userId={}, destination={}", userId, destination);
            } else {
                Long roomId = extractExactRoomId(destination, SUBSCRIBE_PREFIX, false);
                if (roomId == null) {
                    throw new MessagingException("허용되지 않은 WebSocket 구독 경로입니다.");
                }
                validateParticipant(roomId, userId);
                log.debug("[WS SUBSCRIBE] userId={} roomId={}", userId, roomId);
            }

        } else if (StompCommand.SEND.equals(command)) {
            UUID userId = getPrincipalId(accessor);
            Long roomId = extractExactRoomId(accessor.getDestination(), SEND_PREFIX, true);
            if (roomId == null) {
                throw new MessagingException("허용되지 않은 WebSocket 전송 경로입니다.");
            }
            validateParticipant(roomId, userId);
            log.debug("[WS SEND] userId={} roomId={}", userId, roomId);
        }

        return message;
    }

    // ── 헬퍼 메서드 ────────────────────────────────────────────────────────────

    /**
     * WebSocketHandshakeInterceptor 가 핸드셰이크 시 저장한 userId를 꺼냅니다.
     * 핸드셰이크 인터셉터가 인증을 거부하면 연결 자체가 성립되지 않으므로
     * 여기서 userId 가 null 인 경우는 비정상 접근입니다.
     */
    private UUID getUserIdFromAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw new MessagingException("WebSocket 세션 attributes가 없습니다.");
        }
        Object userId = attributes.get(WebSocketHandshakeInterceptor.ATTR_USER_ID);
        if (!(userId instanceof UUID)) {
            throw new MessagingException("인증되지 않은 WebSocket 연결입니다.");
        }
        return (UUID) userId;
    }

    /** accessor에 설정된 principal에서 userId를 꺼냅니다. */
    private UUID getPrincipalId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new MessagingException("인증 정보가 없습니다. CONNECT 요청이 선행되어야 합니다.");
    }

    /**
     * destination에서 prefix 이후의 첫 번째 path segment를 Long으로 파싱합니다.
     * 예) "/topic/chat/42" → 42L
     */
    private Long extractExactRoomId(String destination, String prefix, boolean sendDestination) {
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }
        String remainder = destination.substring(prefix.length());
        String[] segments = remainder.split("/", -1);
        if ((!sendDestination && segments.length != 1)
                || (sendDestination && (segments.length != 2 || !"send".equals(segments[1])))) {
            return null;
        }
        try {
            return Long.parseLong(segments[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isMatchUserDestination(String destination) {
        return MATCH_PROPOSAL_DESTINATION.equals(destination)
                || MATCH_RESULT_DESTINATION.equals(destination);
    }

    /** 채팅방 참여자가 아니면 MessagingException을 던집니다. */
    private void validateParticipant(Long roomId, UUID userId) {
        if (!chatRoomRepository.existsParticipantByRoomIdAndUserId(roomId, userId)) {
            throw new MessagingException("해당 채팅방에 접근할 권한이 없습니다.");
        }
    }
}

