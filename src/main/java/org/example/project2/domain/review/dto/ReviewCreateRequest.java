package org.example.project2.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.project2.domain.review.entity.ImpressionTag;
import org.example.project2.domain.review.entity.RevisitIntention;

/**
 * 매칭 상대방에 대한 후기 작성 요청입니다.
 *
 * <p>작성자와 평가 대상은 인증 사용자와 매칭 참여 관계로 서버가 결정하므로
 * 요청 본문에서 받지 않습니다. {@code impressionTag}는 선택하지 않을 수 있는 단일 태그입니다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ReviewCreateRequest(
        @NotNull(message = "매칭 ID는 필수입니다.")
        @Positive(message = "매칭 ID는 1 이상이어야 합니다.")
        @Schema(description = "후기를 제출할 완료 매칭 ID", example = "301", minimum = "1")
        Long matchId,

        @NotNull(message = "재만남 의향은 필수입니다.")
        @Schema(
                description = "상대방에 대한 재만남 의향 고정 코드",
                allowableValues = {"DEFINITELY_AGAIN", "MAYBE_AGAIN", "ENOUGH_FOR_NOW"},
                example = "DEFINITELY_AGAIN"
        )
        RevisitIntention revisitIntention,

        @Schema(
                description = "선택하지 않아도 되는 인상 태그 고정 코드. 복수 태그는 허용하지 않음",
                nullable = true,
                allowableValues = {"PUNCTUAL", "COMFORTABLE_CONVERSATION", "CONSIDERATE", "ACTIVE_PARTICIPATION"},
                example = "PUNCTUAL"
        )
        ImpressionTag impressionTag
) {
}
