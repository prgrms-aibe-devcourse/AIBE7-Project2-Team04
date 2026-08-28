package org.example.project2.domain.chat.dto;

import java.util.UUID;

public record ChatMessageDTO(
        Long roomId,
        UUID sender,
        String message
) {
}
