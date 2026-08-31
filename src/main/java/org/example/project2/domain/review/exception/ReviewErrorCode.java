package org.example.project2.domain.review.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode {
    INVALID_INPUT("COMMON_002", "요청 값 검증에 실패했습니다.", HttpStatus.BAD_REQUEST),
    FORBIDDEN("AUTH_002", "매칭 참여자만 후기를 작성할 수 있습니다.", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("COMMON_001", "요청한 매칭 또는 사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    REVIEW_NOT_AVAILABLE("REVIEW_NOT_AVAILABLE", "매칭 완료 후에만 후기를 작성할 수 있습니다.", HttpStatus.CONFLICT),
    REVIEW_ALREADY_SUBMITTED("REVIEW_ALREADY_SUBMITTED", "동일한 매칭 상대에게 이미 후기를 작성했습니다.", HttpStatus.CONFLICT),
    REVIEW_PERIOD_EXPIRED("REVIEW_PERIOD_EXPIRED", "후기 작성 기간이 지났습니다.", HttpStatus.GONE),
    DATA_INCONSISTENT("REVIEW_DATA_INVALID", "후기 작성에 필요한 매칭 정보를 확인할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
