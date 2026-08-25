package org.example.project2.domain.personality.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PersonalityErrorCode {
    INVALID_INPUT(
            "PERSONALITY_002",
            "지원하지 않는 설문 버전이거나 성향 입력값이 유효하지 않습니다.",
            HttpStatus.UNPROCESSABLE_CONTENT
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}
