package org.example.project2.domain.chat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.chat.dto.ChatPlaceDTO;
import org.example.project2.domain.chat.entity.ChatMessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDown() {
        validator = null;
    }

    @Test
    void rejectsBlankMessage() {
        Set<?> violations = validator.validate(new ChatMessageDTO(1L, UUID.randomUUID(), " \t"));
        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsMessageLongerThanOneThousandCharacters() {
        Set<?> violations = validator.validate(new ChatMessageDTO(1L, UUID.randomUUID(), "a".repeat(1_001)));
        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsMessageWithinLimit() {
        Set<?> violations = validator.validate(new ChatMessageDTO(1L, UUID.randomUUID(), "a".repeat(1_000)));
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsValidPlaceMessage() {
        ChatPlaceDTO place = new ChatPlaceDTO(
                "123456789",
                "마주식당",
                "음식점 > 한식",
                "서울특별시 강남구 테헤란로 1",
                37.498,
                127.027,
                null
        );

        Set<?> violations = validator.validate(
                new ChatMessageDTO(1L, UUID.randomUUID(), ChatMessageType.PLACE, null, place));

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsPlaceMessageWithoutPlacePayload() {
        Set<?> violations = validator.validate(
                new ChatMessageDTO(1L, UUID.randomUUID(), ChatMessageType.PLACE, null, null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsInvalidProviderPlaceId() {
        ChatPlaceDTO place = new ChatPlaceDTO(
                "javascript:alert(1)",
                "마주식당",
                "음식점 > 한식",
                "서울특별시 강남구 테헤란로 1",
                37.498,
                127.027,
                null
        );

        Set<?> violations = validator.validate(
                new ChatMessageDTO(1L, UUID.randomUUID(), ChatMessageType.PLACE, null, place));

        assertThat(violations).isNotEmpty();
    }
}
