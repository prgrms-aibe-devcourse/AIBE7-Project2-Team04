package org.example.project2.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 인증 사용자가 받은 후기의 집계 요약입니다.
 *
 * <p>개별 후기, 작성자 식별자, 원본 재만남 의향과 태그 통계는 포함하지 않습니다.</p>
 */
@Schema(description = "내가 받은 후기 및 다시한끼 지수 요약")
public record MyReviewSummaryResponse(
        @Schema(
                description = "다시한끼 지수 노출 상태",
                allowableValues = {"NO_REVIEWS", "INSUFFICIENT_REVIEWS", "AVAILABLE"},
                example = "AVAILABLE"
        )
        ReviewScoreStatus scoreStatus,

        @Schema(
                description = "다시한끼 지수. AVAILABLE일 때만 제공하며 0.0~100.0 범위의 소수점 첫째 자리 값. 안전성·신뢰성을 보증하는 절대 척도가 아닌 참고 지표",
                nullable = true,
                example = "84.0"
        )
        BigDecimal dasiHankkiScore,

        @Schema(description = "집계에 사용된 유효 후기 수", example = "8", minimum = "0")
        int validReviewCount
) {
}
