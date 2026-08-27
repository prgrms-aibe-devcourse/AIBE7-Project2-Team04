package org.example.project2.domain.matching.dto.preference;

import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.personality.entity.PersonalityDimension;

public record MatchingPreferenceItemResponse(
        PersonalityDimension dimension,
        short importance,
        PreferenceMode mode
) {
}
