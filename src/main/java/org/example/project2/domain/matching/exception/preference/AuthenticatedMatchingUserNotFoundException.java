package org.example.project2.domain.matching.exception.preference;

public class AuthenticatedMatchingUserNotFoundException extends RuntimeException {
    public AuthenticatedMatchingUserNotFoundException() {
        super("인증된 사용자 정보를 찾을 수 없습니다.");
    }
}
