package org.example.project2.domain.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Set;

/**
 * 실시간 매칭 요청에서 상대에게 바라는 세부 성향 태그 입력입니다.
 *
 * <p>실제 매칭 요청 생성 API가 이 DTO를 포함할 때에도, 요청 시점의 선택값을
 * {@code MatchRequest.desiredPersonalityTags}에 그대로 보존합니다.</p>
 */
public record RealtimeMatchRequestCreateRequest(
        @NotNull(message = "원하는 상대 성향 태그는 필수입니다.")
        @Size(min = 3, max = 5, message = "원하는 상대 성향 태그는 3개 이상 5개 이하로 선택할 수 있습니다.")
        @Schema(
                description = "이번 매칭 요청에서 원하는 상대의 세부 성향 태그. 3개 이상 5개 이하",
                example = "[\"GOOD_LISTENER\", \"FOOD_TALK\", \"ENJOY_DESSERT\"]"
        )
        Set<@NotNull(message = "원하는 상대 성향 태그에는 null을 포함할 수 없습니다.") PersonalityTag> desiredPersonalityTags,

        @Size(max = 300, message = "원하는 상대 성향 설명은 최대 300자까지 입력할 수 있습니다.")
        @Schema(
                description = "이번 매칭 요청에서 원하는 상대 성향에 대한 선택형 자유 서술",
                maxLength = 300,
                example = "대화를 편하게 이어가되 식사 속도가 비슷한 분"
        )
        String desiredPersonalityText
) {
    public RealtimeMatchRequestCreateRequest {
        desiredPersonalityTags = desiredPersonalityTags == null ? null : Set.copyOf(desiredPersonalityTags);
        desiredPersonalityText = normalizeDesiredPersonalityText(desiredPersonalityText);
    }

    private static String normalizeDesiredPersonalityText(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
