package org.example.project2.domain.matching.dto;

import org.example.project2.domain.personality.entity.PersonalityDimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record MatchingPreferenceSnapshot(
        Map<PersonalityDimension, DimensionMatchPreference> dimensions
) {
    public MatchingPreferenceSnapshot {
        if (dimensions == null) {
            throw new IllegalArgumentException("매칭 선호 스냅샷은 필수입니다.");
        }
        EnumMap<PersonalityDimension, DimensionMatchPreference> copied = new EnumMap<>(PersonalityDimension.class);
        copied.putAll(dimensions);
        Set<PersonalityDimension> requiredDimensions = Set.of(PersonalityDimension.values());
        if (!copied.keySet().equals(requiredDimensions)) {
            throw new IllegalArgumentException("매칭 선호 스냅샷에는 네 가지 성향 차원이 중복 없이 모두 포함되어야 합니다.");
        }
        if (copied.containsValue(null)) {
            throw new IllegalArgumentException("성향 차원별 매칭 선호 값은 null일 수 없습니다.");
        }
        dimensions = Map.copyOf(copied);
    }

    public static MatchingPreferenceSnapshot of(Map<PersonalityDimension, DimensionMatchPreference> dimensions) {
        return new MatchingPreferenceSnapshot(dimensions);
    }
}
