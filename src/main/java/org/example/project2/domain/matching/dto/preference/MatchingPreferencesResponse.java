package org.example.project2.domain.matching.dto.preference;

import java.util.List;

public record MatchingPreferencesResponse(
        List<MatchingPreferenceItemResponse> preferences
) {
    public MatchingPreferencesResponse {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }
}
