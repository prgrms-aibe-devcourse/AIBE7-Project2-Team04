package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonalityTagSuggestionRequest(
        @NotBlank(message = "태그 추천을 받을 자기소개를 입력해주세요.")
        @Size(max = 300, message = "자기소개는 최대 300자까지 입력할 수 있습니다.")
        @Schema(maxLength = 300)
        String selfDescription,

        @AssertTrue(message = "AI 분석에 동의해야 태그를 추천받을 수 있습니다.")
        boolean aiAnalysisConsent
) {
    public PersonalityTagSuggestionRequest {
        selfDescription = selfDescription == null ? null : selfDescription.strip();
    }
}
