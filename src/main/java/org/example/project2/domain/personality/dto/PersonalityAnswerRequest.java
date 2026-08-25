package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;

public record PersonalityAnswerRequest(
        @NotNull(message = "성향 차원은 필수입니다.")
        @Schema(description = "성향 차원", example = "CONVERSATION_LEVEL")
        PersonalityDimension questionCode,

        @NotNull(message = "성향 응답값은 필수입니다.")
        @Schema(description = "카드 선택값: 낮음 1, 중간 3, 높음 5", example = "3", allowableValues = {"1", "3", "5"})
        PersonalityAnswerValue value
) {
}
