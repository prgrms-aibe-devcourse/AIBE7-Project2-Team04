package org.example.project2.global.exception.signUp;

public record SignUpErrorResponse(
        boolean success,
        Void data,
        ErrorDetail error
) {
    /*
    SignUpErrorResponse에 errorCode만 들어갈 경우
    errorCode.getMessage() 문자열을 of(SignUpErrorCode errorCode, String message)
    매개 변수로 넘겨버림
     */
    public static SignUpErrorResponse of(SignUpErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    /*
    오류 응답 DTO에서 record로 선언된 ErrorDetail 객체에 errorCode, message 넣어서
    ErrorResponse 생성
     */
    public static SignUpErrorResponse of(SignUpErrorCode errorCode, String message) {
        return new SignUpErrorResponse(
                false,
                null,
                new ErrorDetail(errorCode.getCode(), message)
        );
    }

    public record ErrorDetail(String code, String message) {
    }
}
