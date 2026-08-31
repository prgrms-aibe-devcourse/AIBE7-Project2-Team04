package org.example.project2.domain.review.service;

import org.example.project2.domain.review.repository.ReviewScoreAggregateProjection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 다시한끼 지수의 산식과 버전을 한 곳에서 관리합니다.
 *
 * <p>산식을 변경할 때 버전을 올리고, 기존 후기 집합에 새 버전을 재계산해
 * 변경 전·후 결과와 사용자 불이익 여부를 비교할 수 있도록 합니다. 버전은
 * 내부 정책 식별자이며 MVP API 응답에는 노출하지 않습니다.</p>
 */
@Component
public class ReviewScorePolicy {
    public static final String CURRENT_VERSION = "DASI_HANKKI_V1";

    private static final BigDecimal BASELINE_SCORE = new BigDecimal("2.5");
    private static final BigDecimal BASELINE_REVIEW_COUNT = new BigDecimal("5");
    private static final BigDecimal MAYBE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public BigDecimal calculate(ReviewScoreAggregateProjection aggregate, long validReviewCount) {
        BigDecimal definitelyAgain = BigDecimal.valueOf(valueOrZero(
                aggregate == null ? null : aggregate.getDefinitelyAgainCount()
        ));
        BigDecimal maybeAgain = BigDecimal.valueOf(valueOrZero(
                aggregate == null ? null : aggregate.getMaybeAgainCount()
        ));
        BigDecimal numerator = BASELINE_SCORE
                .add(definitelyAgain)
                .add(maybeAgain.multiply(MAYBE_WEIGHT));
        BigDecimal denominator = BASELINE_REVIEW_COUNT.add(BigDecimal.valueOf(validReviewCount));
        return numerator
                .multiply(HUNDRED)
                .divide(denominator, 1, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }
}
