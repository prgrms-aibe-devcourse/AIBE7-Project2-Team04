package org.example.project2.domain.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.project2.domain.chat.entity.ChatMessageType;

public record ChatMessageListResponse(
        List<MessageItem> content,
        boolean hasNext
) {
    public record MessageItem(
            Long messageId,
            UUID senderId,
            ChatMessageType messageType,
            String content,
            ChatPlaceDTO place,
            Instant sentAt
    ) {}
}
