package org.example.project2.domain.review.exception;

import lombok.Getter;

@Getter
public class ReviewException extends RuntimeException {
    private final ReviewErrorCode errorCode;

    public ReviewException(ReviewErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ReviewException(ReviewErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
