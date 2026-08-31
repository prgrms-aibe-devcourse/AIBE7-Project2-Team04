package org.example.project2.domain.review.dto;

/**
 * 다시한끼 지수의 외부 노출 상태입니다.
 *
 * <p>상태 코드는 API에서 사용하고, 사용자에게 표시할 한국어 문구는
 * 클라이언트에서 매핑합니다.</p>
 */
public enum ReviewScoreStatus {
    /** 받은 유효 후기가 없는 상태입니다. */
    NO_REVIEWS,

    /** 최소 공개 표본 수에 도달하지 않아 점수를 공개하지 않는 상태입니다. */
    INSUFFICIENT_REVIEWS,

    /** 최소 공개 표본 수를 충족해 점수를 공개할 수 있는 상태입니다. */
    AVAILABLE
}
