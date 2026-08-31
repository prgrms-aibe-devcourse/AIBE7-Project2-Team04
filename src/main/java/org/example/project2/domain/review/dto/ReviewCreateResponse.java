package org.example.project2.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 후기 작성 성공 시 반환하는 최소 응답입니다.
 */
@Schema(description = "후기 작성 성공 응답")
public record ReviewCreateResponse(
        @Schema(description = "생성된 후기 ID", example = "901")
        Long reviewId,

        @Schema(description = "후기 제출 시각", example = "2026-08-31T12:00:00Z")
        Instant submittedAt
) {
}
