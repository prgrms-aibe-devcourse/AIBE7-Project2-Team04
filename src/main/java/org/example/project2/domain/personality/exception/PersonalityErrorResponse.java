package org.example.project2.domain.personality.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "성향 API 오류 응답")
public record PersonalityErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static PersonalityErrorResponse of(PersonalityErrorCode errorCode, String message) {
        return new PersonalityErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
