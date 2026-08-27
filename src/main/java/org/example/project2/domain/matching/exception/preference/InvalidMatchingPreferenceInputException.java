package org.example.project2.domain.matching.exception.preference;

import lombok.Getter;

@Getter
public class InvalidMatchingPreferenceInputException extends RuntimeException {
    private final MatchingPreferenceErrorCode errorCode = MatchingPreferenceErrorCode.INVALID_INPUT;

    public InvalidMatchingPreferenceInputException(String message) {
        super(message);
    }
}
