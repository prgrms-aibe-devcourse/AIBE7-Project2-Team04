package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.DesiredPersonalityTagMatchScore;
import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 요청자의 희망 태그 중 후보자가 확정한 태그의 비율을 0~100점으로 계산합니다.
 */
@Component
public class DesiredPersonalityTagScoreCalculator {

    public DesiredPersonalityTagMatchScore calculate(
            Set<PersonalityTag> desiredTags,
            Set<PersonalityTag> candidateStyleTags
    ) {
        if (desiredTags == null || desiredTags.isEmpty()
                || candidateStyleTags == null || candidateStyleTags.isEmpty()) {
            return DesiredPersonalityTagMatchScore.unavailable();
        }

        Set<PersonalityTag> matchedTags = EnumSet.copyOf(desiredTags);
        matchedTags.retainAll(candidateStyleTags);

        short score = (short) Math.round((matchedTags.size() * 100.0) / desiredTags.size());
        return new DesiredPersonalityTagMatchScore(true, score, matchedTags);
    }
}
