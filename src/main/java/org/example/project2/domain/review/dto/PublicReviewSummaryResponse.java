package org.example.project2.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 공개 프로필에서 제공하는 후기 집계 요약입니다.
 *
 * <p>본인 마이페이지 응답과 DTO를 분리해 작성자·매칭·민감 데이터가 섞이지 않도록 합니다.
 * MVP에서는 공개 가능한 개별 후기 목록과 태그 통계를 제공하지 않습니다.</p>
 */
@Schema(description = "공개 프로필용 후기 및 다시한끼 지수 요약")
public record PublicReviewSummaryResponse(
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
