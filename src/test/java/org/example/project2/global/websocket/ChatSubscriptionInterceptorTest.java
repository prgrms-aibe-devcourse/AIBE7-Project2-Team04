package org.example.project2.global.websocket;

import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSubscriptionInterceptorTest {

    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final ChatSubscriptionInterceptor interceptor =
            new ChatSubscriptionInterceptor(chatRoomRepository);

    @Test
    void setsAuthenticatedPrincipalOnConnect() {
        UUID userId = UUID.randomUUID();
        Message<byte[]> message = message(
                StompCommand.CONNECT,
                null,
                null,
                Map.of(WebSocketHandshakeInterceptor.ATTR_USER_ID, userId)
        );

        Message<?> result = interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void allowsOnlyAuthenticatedMatchUserQueueSubscription() {
        UUID userId = UUID.randomUUID();
        var principal = new UsernamePasswordAuthenticationToken(userId, null);
        Message<byte[]> authenticated = message(
                StompCommand.SUBSCRIBE,
                "/user/queue/match-result",
                principal,
                Map.of()
        );
        Message<byte[]> anonymous = message(
                StompCommand.SUBSCRIBE,
                "/user/queue/match-result",
                null,
                Map.of()
        );

        assertThat(interceptor.preSend(
                authenticated,
                mock(org.springframework.messaging.MessageChannel.class)
        )).isSameAs(authenticated);
        assertThatThrownBy(() -> interceptor.preSend(
                anonymous,
                mock(org.springframework.messaging.MessageChannel.class)
        )).isInstanceOf(MessagingException.class);
        verify(chatRoomRepository, never()).existsParticipantByRoomIdAndUserId(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsUnknownSubscriptionAndChecksChatParticipant() {
        UUID userId = UUID.randomUUID();
        var principal = new UsernamePasswordAuthenticationToken(userId, null);
        Message<byte[]> unknown = message(
                StompCommand.SUBSCRIBE,
                "/topic/unknown",
                principal,
                Map.of()
        );
        Message<byte[]> chat = message(
                StompCommand.SUBSCRIBE,
                "/topic/chat/42",
                principal,
                Map.of()
        );
        when(chatRoomRepository.existsParticipantByRoomIdAndUserId(42L, userId)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(
                unknown,
                mock(org.springframework.messaging.MessageChannel.class)
        )).isInstanceOf(MessagingException.class);
        assertThat(interceptor.preSend(
                chat,
                mock(org.springframework.messaging.MessageChannel.class)
        )).isSameAs(chat);
        verify(chatRoomRepository).existsParticipantByRoomIdAndUserId(42L, userId);
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            UsernamePasswordAuthenticationToken principal,
            Map<String, Object> sessionAttributes
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
