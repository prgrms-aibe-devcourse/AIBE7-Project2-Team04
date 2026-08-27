package org.example.project2.domain.matching.dto.preference;

import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.matching.exception.preference.InvalidMatchingPreferenceInputException;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingPreferencesUpdateRequestTest {

    @Test
    void returnsFourUniqueValidatedPreferences() {
        MatchingPreferencesUpdateRequest request = new MatchingPreferencesUpdateRequest(List.of(
                item(PersonalityDimension.CONVERSATION_LEVEL, 5, PreferenceMode.SIMILAR),
                item(PersonalityDimension.MEAL_PACE, 4, PreferenceMode.SIMILAR),
                item(PersonalityDimension.PLANNING_STYLE, 2, PreferenceMode.COMPLEMENTARY),
                item(PersonalityDimension.NOVELTY_PREFERENCE, 3, PreferenceMode.SIMILAR)
        ));

        assertThat(request.validatedPreferences())
                .containsOnlyKeys(PersonalityDimension.values());
    }

    @Test
    void rejectsDuplicateAndMissingDimensionCombination() {
        MatchingPreferencesUpdateRequest request = new MatchingPreferencesUpdateRequest(List.of(
                item(PersonalityDimension.CONVERSATION_LEVEL, 5, PreferenceMode.SIMILAR),
                item(PersonalityDimension.CONVERSATION_LEVEL, 4, PreferenceMode.SIMILAR),
                item(PersonalityDimension.PLANNING_STYLE, 2, PreferenceMode.COMPLEMENTARY),
                item(PersonalityDimension.NOVELTY_PREFERENCE, 3, PreferenceMode.SIMILAR)
        ));

        assertThatThrownBy(request::validatedPreferences)
                .isInstanceOf(InvalidMatchingPreferenceInputException.class)
                .hasMessage("동일한 성향 차원을 중복해서 입력할 수 없습니다.");
    }

    @Test
    void rejectsImportanceOutsideZeroToFiveEvenWithoutBeanValidation() {
        MatchingPreferencesUpdateRequest request = new MatchingPreferencesUpdateRequest(List.of(
                item(PersonalityDimension.CONVERSATION_LEVEL, 6, PreferenceMode.SIMILAR),
                item(PersonalityDimension.MEAL_PACE, 4, PreferenceMode.SIMILAR),
                item(PersonalityDimension.PLANNING_STYLE, 2, PreferenceMode.COMPLEMENTARY),
                item(PersonalityDimension.NOVELTY_PREFERENCE, 3, PreferenceMode.SIMILAR)
        ));

        assertThatThrownBy(request::validatedPreferences)
                .isInstanceOf(InvalidMatchingPreferenceInputException.class)
                .hasMessage("성향 중요도는 0 이상 5 이하여야 합니다.");
    }

    private MatchingPreferenceItemRequest item(
            PersonalityDimension dimension,
            int importance,
            PreferenceMode mode
    ) {
        return new MatchingPreferenceItemRequest(dimension, importance, mode);
    }
}
