package org.example.project2.domain.auth.exception;

/**
 * 일반 로그인 시 이메일 또는 패스워드가 올바르지 않거나
 * 비활성화된 계정일 때 발생하는 예외 클래스입니다.
 */
public class LoginFailedException extends RuntimeException {
    public LoginFailedException() {
        super("이메일 또는 비밀번호가 일치하지 않습니다.");
    }
}
