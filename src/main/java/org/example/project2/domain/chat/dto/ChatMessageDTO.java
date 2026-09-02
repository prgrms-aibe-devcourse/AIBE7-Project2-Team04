package org.example.project2.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.chat.entity.ChatMessageType;

public record ChatMessageDTO(
        @NotNull(message = "채팅방 ID는 필수입니다.")
        Long roomId,
        UUID sender,
        @NotNull(message = "메시지 유형은 필수입니다.")
        ChatMessageType messageType,
        @Size(max = 1_000, message = "메시지는 1,000자 이내로 입력해야 합니다.")
        String message,
        @Valid
        ChatPlaceDTO place
) {
    public ChatMessageDTO {
        messageType = messageType == null ? ChatMessageType.TEXT : messageType;
    }

    public ChatMessageDTO(Long roomId, UUID sender, String message) {
        this(roomId, sender, ChatMessageType.TEXT, message, null);
    }

    @JsonIgnore
    @AssertTrue(message = "메시지 유형에 맞는 내용을 입력해주세요.")
    public boolean isPayloadValid() {
        return switch (messageType) {
            case TEXT -> message != null && !message.isBlank() && place == null;
            case PLACE -> place != null;
        };
    }
}
