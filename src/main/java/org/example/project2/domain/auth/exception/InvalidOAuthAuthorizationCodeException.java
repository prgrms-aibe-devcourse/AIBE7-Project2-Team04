package org.example.project2.domain.auth.exception;

public class InvalidOAuthAuthorizationCodeException extends RuntimeException {
    public InvalidOAuthAuthorizationCodeException() {
        super("유효하지 않거나 만료된 OAuth 인증 코드입니다.");
    }
}
