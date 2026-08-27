package org.example.project2.domain.matching.dto.preference;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.personality.entity.PersonalityDimension;

public record MatchingPreferenceItemRequest(
        @NotNull(message = "성향 차원은 필수입니다.")
        PersonalityDimension dimension,

        @Min(value = 0, message = "성향 중요도는 0 이상이어야 합니다.")
        @Max(value = 5, message = "성향 중요도는 5 이하여야 합니다.")
        int importance,

        @NotNull(message = "성향 선호 방식은 필수입니다.")
        PreferenceMode mode
) {
}
