package org.example.project2.domain.matching.exception.result;

import lombok.Getter;

@Getter
public class MatchResultException extends RuntimeException {
    private final MatchResultErrorCode errorCode;

    public MatchResultException(MatchResultErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
