package org.example.project2.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/message")
    public void message(ChatMessageDTO message){
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.roomId(), message);
    }
}
