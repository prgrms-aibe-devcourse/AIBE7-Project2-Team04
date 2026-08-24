package org.example.project2.global.exception.signUp;

import lombok.Getter;

@Getter
public class SignUpException extends RuntimeException {
    private final SignUpErrorCode errorCode;

    /*
    Java 표준 예외 쳬계와 서버 로그/모니터링 도구가 에러 메시지를 인식할 수 있도록 부모에게 전달하고 -> super(errorCode.getMessage())
    현재 프로젝트의 전역 핸들러가 에러 코드와 상태 코드를 꺼내 쓸 수 있도록 필드로 저장 -> this.errorCode = errorCode;
     */
    protected SignUpException(SignUpErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
