package org.example.project2.domain.review.repository;

/**
 * 공개 가능한 후기만 한 번의 집계 쿼리로 읽기 위한 Projection입니다.
 */
public interface ReviewScoreAggregateProjection {
    Long getValidReviewCount();

    Long getDefinitelyAgainCount();

    Long getMaybeAgainCount();

    Long getEnoughForNowCount();
}
