package org.example.project2.domain.matching.exception.preference;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchingPreferenceErrorCode {
    INVALID_INPUT(
            "MATCHING_001",
            "상대방 선호 입력값이 유효하지 않습니다.",
            HttpStatus.UNPROCESSABLE_CONTENT
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}
