package org.example.project2.domain.matching.dto.scoring;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Set;

public record PersonalityCompatibilityScore(
        boolean available,
        short score,
        Short structuredScore,
        Short cardScore,
        Short tagScore,
        Short embeddingScore,
        Set<PersonalityTag> matchedTags,
        String formulaVersion
) {
    public PersonalityCompatibilityScore {
        validateScore(score, "최종 성향 호환도");
        validateNullableScore(structuredScore, "정형 성향 호환도");
        validateNullableScore(cardScore, "카드 성향 호환도");
        validateNullableScore(tagScore, "태그 성향 호환도");
        validateNullableScore(embeddingScore, "임베딩 성향 호환도");
        matchedTags = matchedTags == null ? Set.of() : Set.copyOf(matchedTags);
    }

    public static PersonalityCompatibilityScore unavailable(String formulaVersion) {
        return new PersonalityCompatibilityScore(
                false, (short) 0, null, null, null, null, Set.of(), formulaVersion
        );
    }

    private static void validateNullableScore(Short score, String name) {
        if (score != null) {
            validateScore(score, name);
        }
    }

    private static void validateScore(short score, String name) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(name + " 점수는 0 이상 100 이하여야 합니다.");
        }
    }
}
