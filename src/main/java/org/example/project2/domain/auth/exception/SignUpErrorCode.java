package org.example.project2.domain.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SignUpErrorCode {
    /*
    예외 종류 enum 타입으로 미리 지정
    status, code, message는 enum type에 따라 자동으로 필드 값 초기화
     */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 값 검증에 실패했습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_006", "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_007", "이미 사용 중인 닉네임입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
