package org.example.project2.domain.review.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 후기 API 오류 응답")
public record ReviewErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static ReviewErrorResponse of(ReviewErrorCode errorCode, String message) {
        return new ReviewErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
