package org.example.project2.domain.user.exception;

import lombok.Getter;

@Getter
public class LocationException extends RuntimeException {
    private final LocationErrorCode errorCode;

    public LocationException(LocationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public LocationException(LocationErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
