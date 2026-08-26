package org.example.project2.domain.user.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "위치 API 오류 응답")
public record LocationErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static LocationErrorResponse of(LocationErrorCode errorCode, String message) {
        return new LocationErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
