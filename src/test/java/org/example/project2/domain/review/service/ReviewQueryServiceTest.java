package org.example.project2.domain.review.service;

import org.example.project2.domain.review.dto.ReviewScoreStatus;
import org.example.project2.domain.review.entity.RevisitIntention;
import org.example.project2.domain.review.entity.UserReviewVisibility;
import org.example.project2.domain.review.exception.ReviewErrorCode;
import org.example.project2.domain.review.exception.ReviewException;
import org.example.project2.domain.review.repository.ReviewScoreAggregateProjection;
import org.example.project2.domain.review.repository.UserReviewRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {
    @Mock UserReviewRepository userReviewRepository;
    @Mock UserRepository userRepository;
    @Mock ReviewScoreAggregateProjection aggregate;

    private ReviewQueryService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new ReviewQueryService(userReviewRepository, userRepository, new ReviewScorePolicy());
        userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId)
                .email("user@test.com")
                .nickname("사용자")
                .status(UserStatus.ACTIVE)
                .build()));
    }

    @Test
    void returnsNoReviewsStateWithoutScore() {
        givenAggregate(0L, 0L, 0L, 0L);

        var response = service.getMyReviewSummary(userId);

        assertThat(response.scoreStatus()).isEqualTo(ReviewScoreStatus.NO_REVIEWS);
        assertThat(response.dasiHankkiScore()).isNull();
        assertThat(response.validReviewCount()).isZero();
    }

    @Test
    void hidesScoreUntilThreePublicReviewsExist() {
        givenAggregate(2L, 1L, 1L, 0L);

        var response = service.getPublicReviewSummary(userId);

        assertThat(response.scoreStatus()).isEqualTo(ReviewScoreStatus.INSUFFICIENT_REVIEWS);
        assertThat(response.dasiHankkiScore()).isNull();
        assertThat(response.validReviewCount()).isEqualTo(2);
    }

    @Test
    void hidesScoreForOnePublicReview() {
        givenAggregate(1L, 0L, 0L, 1L);

        var response = service.getMyReviewSummary(userId);

        assertThat(response.scoreStatus()).isEqualTo(ReviewScoreStatus.INSUFFICIENT_REVIEWS);
        assertThat(response.dasiHankkiScore()).isNull();
        assertThat(response.validReviewCount()).isEqualTo(1);
    }

    @Test
    void calculatesPublishedScoreWithTheFixedPriorAndOneDecimalRounding() {
        givenAggregate(3L, 3L, 0L, 0L);

        var response = service.getMyReviewSummary(userId);

        assertThat(response.scoreStatus()).isEqualTo(ReviewScoreStatus.AVAILABLE);
        assertThat(response.dasiHankkiScore()).isEqualByComparingTo(new BigDecimal("68.8"));
        assertThat(response.validReviewCount()).isEqualTo(3);
    }

    @Test
    void queriesOnlyPublicReviewsWithAllThreeIntentionParameters() {
        givenAggregate(3L, 1L, 1L, 1L);

        service.getMyReviewSummary(userId);

        verify(userReviewRepository).aggregateByRevieweeIdAndVisibility(
                eq(userId),
                eq(UserReviewVisibility.PUBLIC),
                eq(RevisitIntention.DEFINITELY_AGAIN),
                eq(RevisitIntention.MAYBE_AGAIN),
                eq(RevisitIntention.ENOUGH_FOR_NOW)
        );
    }

    @Test
    void mySummaryUsesAuthenticatedUserOnlyAsTheRevieweeFilter() {
        givenAggregate(3L, 1L, 1L, 1L);

        var response = service.getMyReviewSummary(userId);

        assertThat(response.validReviewCount()).isEqualTo(3);
        verify(userReviewRepository).aggregateByRevieweeIdAndVisibility(
                eq(userId),
                eq(UserReviewVisibility.PUBLIC),
                eq(RevisitIntention.DEFINITELY_AGAIN),
                eq(RevisitIntention.MAYBE_AGAIN),
                eq(RevisitIntention.ENOUGH_FOR_NOW)
        );
        verifyNoMoreInteractions(userReviewRepository);
    }

    @Test
    void doesNotExposeSummaryForWithdrawnUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId)
                .email("withdrawn@test.com")
                .nickname("탈퇴 사용자")
                .status(UserStatus.WITHDRAWN)
                .build()));

        assertThatThrownBy(() -> service.getPublicReviewSummary(userId))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.RESOURCE_NOT_FOUND);
    }

    private void givenAggregate(long total, long definitelyAgain, long maybeAgain, long enoughForNow) {
        lenient().when(userReviewRepository.aggregateByRevieweeIdAndVisibility(
                eq(userId),
                eq(UserReviewVisibility.PUBLIC),
                eq(RevisitIntention.DEFINITELY_AGAIN),
                eq(RevisitIntention.MAYBE_AGAIN),
                eq(RevisitIntention.ENOUGH_FOR_NOW)
        )).thenReturn(aggregate);
        lenient().when(aggregate.getValidReviewCount()).thenReturn(total);
        lenient().when(aggregate.getDefinitelyAgainCount()).thenReturn(definitelyAgain);
        lenient().when(aggregate.getMaybeAgainCount()).thenReturn(maybeAgain);
        lenient().when(aggregate.getEnoughForNowCount()).thenReturn(enoughForNow);
    }
}
