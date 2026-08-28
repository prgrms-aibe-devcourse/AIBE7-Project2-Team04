package org.example.project2.domain.matching.dto.result;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.List;

/**
 * 최종 매칭 결과에 포함할 수 있는 공개 호환성 정보입니다.
 * 임베딩 벡터나 원문은 절대 포함하지 않습니다.
 */
public record MatchResultCompatibilityResponse(
        Short score,
        List<PersonalityTag> matchedTags,
        List<String> reasons,
        String formulaVersion
) {
    public MatchResultCompatibilityResponse {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("호환도 점수는 0 이상 100 이하여야 합니다.");
        }
        matchedTags = matchedTags == null ? List.of() : List.copyOf(matchedTags);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
