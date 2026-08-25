package org.example.project2.domain.personality.exception;

import lombok.Getter;

@Getter
public class InvalidPersonalityInputException extends RuntimeException {
    private final PersonalityErrorCode errorCode = PersonalityErrorCode.INVALID_INPUT;

    public InvalidPersonalityInputException(String message) {
        super(message);
    }
}
