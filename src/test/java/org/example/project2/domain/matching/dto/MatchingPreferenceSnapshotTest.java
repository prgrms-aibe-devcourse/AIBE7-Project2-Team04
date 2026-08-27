package org.example.project2.domain.matching.dto;

import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PreferenceMode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingPreferenceSnapshotTest {

    @Test
    void requiresAllFourPersonalityDimensions() {
        Map<PersonalityDimension, DimensionMatchPreference> incomplete = new EnumMap<>(PersonalityDimension.class);
        incomplete.put(PersonalityDimension.CONVERSATION_LEVEL,
                new DimensionMatchPreference((short) 3, PreferenceMode.SIMILAR));

        assertThatThrownBy(() -> MatchingPreferenceSnapshot.of(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매칭 선호 스냅샷에는 네 가지 성향 차원이 중복 없이 모두 포함되어야 합니다.");
    }

    @Test
    void copiesAllDimensionsAsImmutableMap() {
        EnumMap<PersonalityDimension, DimensionMatchPreference> preferences = completePreferences();
        MatchingPreferenceSnapshot snapshot = MatchingPreferenceSnapshot.of(preferences);
        preferences.clear();

        assertThat(snapshot.dimensions()).hasSize(PersonalityDimension.values().length);
        assertThatThrownBy(() -> snapshot.dimensions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    static EnumMap<PersonalityDimension, DimensionMatchPreference> completePreferences() {
        EnumMap<PersonalityDimension, DimensionMatchPreference> preferences =
                new EnumMap<>(PersonalityDimension.class);
        for (PersonalityDimension dimension : PersonalityDimension.values()) {
            preferences.put(dimension, new DimensionMatchPreference((short) 3, PreferenceMode.SIMILAR));
        }
        return preferences;
    }
}
