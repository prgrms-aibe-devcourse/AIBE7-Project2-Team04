package org.example.project2.domain.matching.dto.preference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.matching.exception.preference.InvalidMatchingPreferenceInputException;
import org.example.project2.domain.personality.entity.PersonalityDimension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record MatchingPreferencesUpdateRequest(
        @NotNull(message = "매칭 선호 목록은 필수입니다.")
        @Size(min = 4, max = 4, message = "매칭 선호는 네 가지 성향 차원을 모두 포함해야 합니다.")
        List<@Valid MatchingPreferenceItemRequest> preferences
) {
    public Map<PersonalityDimension, MatchingPreferenceItemRequest> validatedPreferences() {
        if (preferences == null || preferences.size() != PersonalityDimension.values().length) {
            throw new InvalidMatchingPreferenceInputException(
                    "매칭 선호는 네 가지 성향 차원을 모두 포함해야 합니다."
            );
        }

        EnumMap<PersonalityDimension, MatchingPreferenceItemRequest> result =
                new EnumMap<>(PersonalityDimension.class);
        for (MatchingPreferenceItemRequest preference : preferences) {
            if (preference == null || preference.dimension() == null || preference.mode() == null) {
                throw new InvalidMatchingPreferenceInputException("성향 차원과 선호 방식은 필수입니다.");
            }
            if (preference.importance() < 0 || preference.importance() > 5) {
                throw new InvalidMatchingPreferenceInputException("성향 중요도는 0 이상 5 이하여야 합니다.");
            }
            if (result.putIfAbsent(preference.dimension(), preference) != null) {
                throw new InvalidMatchingPreferenceInputException("동일한 성향 차원을 중복해서 입력할 수 없습니다.");
            }
        }
        if (result.size() != PersonalityDimension.values().length) {
            throw new InvalidMatchingPreferenceInputException(
                    "매칭 선호는 네 가지 성향 차원을 중복 없이 모두 포함해야 합니다."
            );
        }
        return Map.copyOf(result);
    }
}
