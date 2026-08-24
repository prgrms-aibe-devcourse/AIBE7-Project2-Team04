package org.example.project2.global.security.handler;

public record SecurityErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    public static SecurityErrorResponse from(SecurityErrorCode errorCode) {
        return new SecurityErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), errorCode.getMessage())
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
