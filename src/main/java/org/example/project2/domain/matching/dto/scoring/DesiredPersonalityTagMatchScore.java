package org.example.project2.domain.matching.dto.scoring;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Set;

/**
 * 요청자가 선택한 희망 태그와 후보자의 확정 태그 간 일치 결과입니다.
 */
public record DesiredPersonalityTagMatchScore(
        boolean available,
        short score,
        Set<PersonalityTag> matchedTags
) {
    public DesiredPersonalityTagMatchScore {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("희망 태그 일치 점수는 0점 이상 100점 이하여야 합니다.");
        }
        matchedTags = matchedTags == null ? Set.of() : Set.copyOf(matchedTags);
    }

    public static DesiredPersonalityTagMatchScore unavailable() {
        return new DesiredPersonalityTagMatchScore(false, (short) 0, Set.of());
    }
}
