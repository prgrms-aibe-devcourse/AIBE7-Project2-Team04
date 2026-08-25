package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PersonalityScoresResponse(
        @Schema(example = "50") short conversationLevel,
        @Schema(example = "50") short mealPace,
        @Schema(example = "50") short planningStyle,
        @Schema(example = "50") short noveltyPreference
) {
}
