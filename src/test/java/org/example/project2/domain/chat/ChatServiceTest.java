package org.example.project2.domain.chat;

import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.chat.dto.ChatPlaceDTO;
import org.example.project2.domain.chat.entity.ChatMessage;
import org.example.project2.domain.chat.entity.ChatMessageType;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.entity.ChatRoomStatus;
import org.example.project2.domain.chat.repository.ChatMessageRepository;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.chat.service.ChatService;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ChatServiceTest {
    @Test
    void doesNotSaveMessageToClosedRoom() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatService service = new ChatService(messageRepository, roomRepository, userRepository);
        ChatRoom room = mock(ChatRoom.class);
        Long roomId = 42L;

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(ChatRoomStatus.CLOSED);

        assertThatThrownBy(() -> service.saveMessage(
                new ChatMessageDTO(roomId, UUID.randomUUID(), "메시지")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("종료된 채팅방에는 메시지를 저장할 수 없습니다.");
        verifyNoInteractions(userRepository, messageRepository);
    }

    @Test
    void savesMessageToActiveRoom() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatService service = new ChatService(messageRepository, roomRepository, userRepository);
        ChatRoom room = mock(ChatRoom.class);
        User user = mock(User.class);
        Long roomId = 42L;
        UUID userId = UUID.randomUUID();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(ChatRoomStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveMessage(new ChatMessageDTO(roomId, userId, "메시지"));

        verify(messageRepository).save(any(ChatMessage.class));
    }

    @Test
    void savesNormalizedPlaceSnapshot() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatService service = new ChatService(messageRepository, roomRepository, userRepository);
        ChatRoom room = mock(ChatRoom.class);
        User user = mock(User.class);
        Long roomId = 42L;
        UUID userId = UUID.randomUUID();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(ChatRoomStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatPlaceDTO place = new ChatPlaceDTO(
                "123456789",
                " 마주식당 ",
                " 음식점 > 한식 ",
                " 서울특별시 강남구 테헤란로 1 ",
                37.498,
                127.027,
                "https://malicious.example/place"
        );
        service.saveMessage(new ChatMessageDTO(
                roomId, userId, ChatMessageType.PLACE, null, place));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertThat(saved.getMessageType()).isEqualTo(ChatMessageType.PLACE);
        assertThat(saved.getContent()).isEqualTo("마주식당");
        assertThat(saved.getPlaceName()).isEqualTo("마주식당");
        assertThat(saved.getPlaceLocation().getX()).isEqualTo(127.027);
        assertThat(saved.getPlaceLocation().getY()).isEqualTo(37.498);
        assertThat(saved.getPlaceUrl()).isEqualTo("https://place.map.kakao.com/123456789");
    }
}
