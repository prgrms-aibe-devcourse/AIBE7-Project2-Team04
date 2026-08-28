package org.example.project2.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    /**
     * 클라이언트가 /app/chat/{roomId}/send 로 메시지를 전송하면 호출됩니다.
     *
     * <p>sender는 클라이언트 payload를 신뢰하지 않고,
     * ChatSubscriptionInterceptor 가 CONNECT 시 설정한 principal에서 추출합니다.</p>
     *
     * @param roomId    destination path variable
     * @param payload   클라이언트가 보낸 메시지 본문 (content 필드만 사용)
     * @param principal 인터셉터가 설정한 인증 주체 (principal.getName() = userId)
     */
    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload ChatMessageDTO payload,
                            Principal principal) {
        // principal.getName()은 ChatSubscriptionInterceptor 에서 설정한 userId(UUID 문자열)
        UUID senderId = UUID.fromString(principal.getName());

        ChatMessageDTO message = new ChatMessageDTO(roomId, senderId, payload.message());

        // DB 저장
        chatService.saveMessage(message);

        // /topic/chat/{roomId} 구독자 전체에게 브로드캐스트
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
    }
}

