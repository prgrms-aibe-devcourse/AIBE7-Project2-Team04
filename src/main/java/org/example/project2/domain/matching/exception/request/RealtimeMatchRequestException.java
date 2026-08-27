package org.example.project2.domain.matching.exception.request;

import lombok.Getter;

@Getter
public class RealtimeMatchRequestException extends RuntimeException {
    private final RealtimeMatchRequestErrorCode errorCode;

    public RealtimeMatchRequestException(RealtimeMatchRequestErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public RealtimeMatchRequestException(
            RealtimeMatchRequestErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}
