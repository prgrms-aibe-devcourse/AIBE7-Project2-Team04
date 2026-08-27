package org.example.project2.domain.matching.exception.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실시간 매칭 요청 API 오류 응답")
public record RealtimeMatchRequestErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static RealtimeMatchRequestErrorResponse of(
            RealtimeMatchRequestErrorCode errorCode,
            String message
    ) {
        return new RealtimeMatchRequestErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
