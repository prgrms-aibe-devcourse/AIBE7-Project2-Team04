package org.example.project2.domain.personality.exception;

public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException() {
        super("인증된 사용자 정보를 찾을 수 없습니다.");
    }
}
