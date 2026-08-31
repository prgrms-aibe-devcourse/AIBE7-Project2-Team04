package org.example.project2.domain.review.exception;

/**
 * 후기 API에서 인증 사용자 식별자를 확인할 수 없을 때 사용하는 예외입니다.
 */
public class AuthenticatedReviewUserNotFoundException extends RuntimeException {
    public AuthenticatedReviewUserNotFoundException() {
        super("인증 사용자를 확인할 수 없습니다.");
    }
}
