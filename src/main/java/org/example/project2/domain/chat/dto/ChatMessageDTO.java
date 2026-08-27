package org.example.project2.domain.chat.dto;

public record ChatMessageDTO(
        String roomId,
        String sender,
        String message
) {
}
