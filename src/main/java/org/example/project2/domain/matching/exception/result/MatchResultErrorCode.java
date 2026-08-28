package org.example.project2.domain.matching.exception.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchResultErrorCode {
    RESULT_NOT_FOUND("MATCHING_013", "현재 확인할 수 있는 매칭 결과가 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
