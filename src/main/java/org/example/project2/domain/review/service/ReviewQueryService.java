package org.example.project2.domain.review.service;

import org.example.project2.domain.review.dto.MyReviewSummaryResponse;
import org.example.project2.domain.review.dto.PublicReviewSummaryResponse;
import org.example.project2.domain.review.dto.ReviewScoreStatus;
import org.example.project2.domain.review.entity.RevisitIntention;
import org.example.project2.domain.review.entity.UserReviewVisibility;
import org.example.project2.domain.review.exception.AuthenticatedReviewUserNotFoundException;
import org.example.project2.domain.review.exception.ReviewErrorCode;
import org.example.project2.domain.review.exception.ReviewException;
import org.example.project2.domain.review.repository.ReviewScoreAggregateProjection;
import org.example.project2.domain.review.repository.UserReviewRepository;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReviewQueryService {
    private static final int MINIMUM_PUBLIC_REVIEWS = 3;

    private final UserReviewRepository userReviewRepository;
    private final UserRepository userRepository;
    private final ReviewScorePolicy reviewScorePolicy;

    @Autowired
    public ReviewQueryService(
            UserReviewRepository userReviewRepository,
            UserRepository userRepository,
            ReviewScorePolicy reviewScorePolicy
    ) {
        this.userReviewRepository = userReviewRepository;
        this.userRepository = userRepository;
        this.reviewScorePolicy = reviewScorePolicy;
    }

    /**
     * 산식 정책을 직접 주입하지 않는 단위 테스트·기존 호출부를 위한 기본 생성자입니다.
     * Spring은 필수 의존성이 모두 있는 Lombok 생성자를 사용합니다.
     */
    public ReviewQueryService(UserReviewRepository userReviewRepository, UserRepository userRepository) {
        this(userReviewRepository, userRepository, new ReviewScorePolicy());
    }

    public MyReviewSummaryResponse getMyReviewSummary(UUID userId) {
        if (userId == null) {
            throw new AuthenticatedReviewUserNotFoundException();
        }
        requireActiveUser(userId);
        ReviewSummary summary = summarize(userId);
        return new MyReviewSummaryResponse(summary.status(), summary.score(), summary.validReviewCount());
    }

    public PublicReviewSummaryResponse getPublicReviewSummary(UUID userId) {
        requireActiveUser(userId);
        ReviewSummary summary = summarize(userId);
        return new PublicReviewSummaryResponse(summary.status(), summary.score(), summary.validReviewCount());
    }

    private ReviewSummary summarize(UUID revieweeId) {
        ReviewScoreAggregateProjection aggregate = userReviewRepository.aggregateByRevieweeIdAndVisibility(
                revieweeId,
                UserReviewVisibility.PUBLIC,
                RevisitIntention.DEFINITELY_AGAIN,
                RevisitIntention.MAYBE_AGAIN,
                RevisitIntention.ENOUGH_FOR_NOW
        );
        long validReviewCount = valueOrZero(aggregate == null ? null : aggregate.getValidReviewCount());
        int count = toIntCount(validReviewCount);
        if (count == 0) {
            return new ReviewSummary(ReviewScoreStatus.NO_REVIEWS, null, count);
        }
        if (count < MINIMUM_PUBLIC_REVIEWS) {
            return new ReviewSummary(ReviewScoreStatus.INSUFFICIENT_REVIEWS, null, count);
        }
        BigDecimal score = reviewScorePolicy.calculate(aggregate, validReviewCount);
        return new ReviewSummary(ReviewScoreStatus.AVAILABLE, score, count);
    }

    private User requireActiveUser(UUID userId) {
        if (userId == null) {
            throw new ReviewException(ReviewErrorCode.RESOURCE_NOT_FOUND);
        }
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ReviewException(ReviewErrorCode.RESOURCE_NOT_FOUND));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private record ReviewSummary(
            ReviewScoreStatus status,
            BigDecimal score,
            int validReviewCount
    ) {
    }
}
