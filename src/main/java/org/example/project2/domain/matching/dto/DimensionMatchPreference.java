package org.example.project2.domain.matching.dto;

import org.example.project2.domain.personality.entity.PreferenceMode;

public record DimensionMatchPreference(
        short importance,
        PreferenceMode mode
) {
    public DimensionMatchPreference {
        if (importance < 0 || importance > 5) {
            throw new IllegalArgumentException("성향 중요도는 0 이상 5 이하여야 합니다.");
        }
        if (mode == null) {
            throw new IllegalArgumentException("성향 선호 방식은 필수입니다.");
        }
    }
}
