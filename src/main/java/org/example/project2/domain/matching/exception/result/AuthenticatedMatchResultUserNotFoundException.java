package org.example.project2.domain.matching.exception.result;

public class AuthenticatedMatchResultUserNotFoundException extends RuntimeException {
    public AuthenticatedMatchResultUserNotFoundException() {
        super("인증된 사용자 정보를 확인할 수 없습니다.");
    }
}
