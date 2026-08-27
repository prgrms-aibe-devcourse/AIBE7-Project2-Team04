package org.example.project2.domain.matching.exception.preference;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상대방 매칭 선호 API 오류 응답")
public record MatchingPreferenceErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static MatchingPreferenceErrorResponse of(
            MatchingPreferenceErrorCode errorCode,
            String message
    ) {
        return new MatchingPreferenceErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
