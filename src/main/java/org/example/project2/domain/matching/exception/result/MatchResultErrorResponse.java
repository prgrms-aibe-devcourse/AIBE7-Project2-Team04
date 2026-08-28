package org.example.project2.domain.matching.exception.result;

public record MatchResultErrorResponse(
        boolean success,
        String code,
        String message
) {
    public static MatchResultErrorResponse from(MatchResultErrorCode errorCode) {
        return new MatchResultErrorResponse(false, errorCode.getCode(), errorCode.getMessage());
    }
}
