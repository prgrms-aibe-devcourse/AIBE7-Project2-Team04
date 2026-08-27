package org.example.project2.domain.matching.exception.request;

public class AuthenticatedRealtimeMatchUserNotFoundException extends RuntimeException {
    public AuthenticatedRealtimeMatchUserNotFoundException() {
        super("인증된 사용자 정보를 찾을 수 없습니다.");
    }
}
