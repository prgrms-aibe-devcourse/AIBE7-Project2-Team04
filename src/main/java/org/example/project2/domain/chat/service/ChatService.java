package org.example.project2.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.chat.entity.ChatMessage;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatMessageRepository;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    /**
     * 채팅 메시지를 DB에 영구 저장합니다.
     */
    @Transactional
    public ChatMessage saveMessage(ChatMessageDTO messageDto) {
        // 1. 채팅방 조회
        Long roomId = messageDto.roomId();
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다. ID: " + roomId));

        // 2. 송신자 조회 (sender 필드가 닉네임일 수도 있고 UUID일 수도 있습니다. 여기서는 UUID 스트링이라고 가정합니다)
        UUID senderId = messageDto.sender();
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + senderId));

        // 3. 메시지 엔티티 빌드 및 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(messageDto.message())
                .build();

        return chatMessageRepository.save(chatMessage);
    }
}
