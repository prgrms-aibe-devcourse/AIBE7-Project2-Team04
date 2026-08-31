package org.example.project2.domain.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.exception.InvalidMatchParticipantsException;
import org.example.project2.domain.matching.exception.MatchNotCompletedException;
import org.example.project2.domain.matching.exception.MatchNotFoundException;
import org.example.project2.domain.matching.exception.NotMatchParticipantException;
import org.example.project2.domain.matching.service.MatchParticipationQueryService;
import org.example.project2.domain.review.dto.ReviewCreateRequest;
import org.example.project2.domain.review.dto.ReviewCreateResponse;
import org.example.project2.domain.review.entity.UserReview;
import org.example.project2.domain.review.exception.AuthenticatedReviewUserNotFoundException;
import org.example.project2.domain.review.exception.ReviewErrorCode;
import org.example.project2.domain.review.exception.ReviewException;
import org.example.project2.domain.review.event.ReviewAuditEventPublisher;
import org.example.project2.domain.review.repository.UserReviewRepository;
import org.example.project2.domain.user.entity.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewCommandService {
    private static final String DUPLICATE_REVIEW_CONSTRAINT = "uk_user_review_match_reviewer_reviewee";
    private static final Duration REVIEW_PERIOD = Duration.ofDays(7);

    private final MatchParticipationQueryService matchParticipationQueryService;
    private final UserReviewRepository userReviewRepository;
    private final Clock clock;
    private final ReviewAuditEventPublisher reviewAuditEventPublisher;

    /**
     * 완료된 매칭의 상대방에게 후기를 한 번만 저장합니다.
     * 후기 요약은 별도 테이블 없이 원본 후기 집계 쿼리로 계산하므로 이 트랜잭션에서 별도 갱신을 하지 않습니다.
     */
    @Transactional
    public ReviewCreateResponse create(UUID reviewerId, ReviewCreateRequest request) {
        validateRequest(reviewerId, request);

        MatchParticipationQueryService.MatchParticipation participation = findParticipation(
                request.matchId(),
                reviewerId
        );
        validateResolvedParticipants(reviewerId, participation);
        if (participation.reviewer().getStatus() != UserStatus.ACTIVE
                || participation.reviewee().getStatus() != UserStatus.ACTIVE) {
            throw new ReviewException(
                    ReviewErrorCode.FORBIDDEN,
                    "활성 상태의 매칭 참여자만 후기를 작성할 수 있습니다."
            );
        }

        if (userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(
                request.matchId(),
                reviewerId,
                participation.reviewee().getId()
        )) {
            reviewAuditEventPublisher.duplicateRejected(
                    request.matchId(), reviewerId, participation.reviewee().getId()
            );
            throw new ReviewException(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
        }

        Instant submittedAt = clock.instant();
        Instant reviewDeadline = participation.match().getEndedAt().plus(REVIEW_PERIOD);
        if (!submittedAt.isBefore(reviewDeadline)) {
            reviewAuditEventPublisher.periodExpired(
                    request.matchId(), reviewerId, participation.reviewee().getId()
            );
            throw new ReviewException(ReviewErrorCode.REVIEW_PERIOD_EXPIRED);
        }

        UserReview review = UserReview.builder()
                .match(participation.match())
                .reviewer(participation.reviewer())
                .reviewee(participation.reviewee())
                .revisitIntention(request.revisitIntention())
                .impressionTag(request.impressionTag())
                .build();

        UserReview savedReview;
        try {
            // saveAndFlush로 경쟁 요청의 DB Unique 충돌을 이 서비스 경계 안에서 변환합니다.
            savedReview = userReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateReviewViolation(exception)) {
                reviewAuditEventPublisher.duplicateRejected(
                        request.matchId(), reviewerId, participation.reviewee().getId()
                );
                throw new ReviewException(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
            }

            log.error("후기 저장 중 무결성 오류가 발생했습니다. matchId={}", request.matchId(), exception);
            throw new ReviewException(ReviewErrorCode.DATA_INCONSISTENT);
        }
        if (savedReview == null || savedReview.getId() == null) {
            throw new ReviewException(ReviewErrorCode.DATA_INCONSISTENT);
        }
        reviewAuditEventPublisher.reviewSubmitted(
                savedReview.getId(),
                request.matchId(),
                reviewerId,
                participation.reviewee().getId(),
                submittedAt
        );
        return new ReviewCreateResponse(savedReview.getId(), submittedAt);
    }

    private boolean isDuplicateReviewViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                return DUPLICATE_REVIEW_CONSTRAINT.equals(constraintViolation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private MatchParticipationQueryService.MatchParticipation findParticipation(
            Long matchId,
            UUID reviewerId
    ) {
        try {
            return matchParticipationQueryService.findCompletedParticipation(matchId, reviewerId);
        } catch (MatchNotFoundException exception) {
            throw new ReviewException(ReviewErrorCode.RESOURCE_NOT_FOUND);
        } catch (NotMatchParticipantException exception) {
            // 존재하는 matchId와 비참여자를 같은 응답으로 처리해 ID 추측으로
            // 매칭 존재 여부나 상대방 정보를 확인할 수 없게 합니다.
            throw new ReviewException(ReviewErrorCode.RESOURCE_NOT_FOUND);
        } catch (MatchNotCompletedException exception) {
            throw new ReviewException(ReviewErrorCode.REVIEW_NOT_AVAILABLE);
        } catch (InvalidMatchParticipantsException exception) {
            throw new ReviewException(ReviewErrorCode.DATA_INCONSISTENT);
        }
    }

    private void validateRequest(UUID reviewerId, ReviewCreateRequest request) {
        if (reviewerId == null) {
            throw new AuthenticatedReviewUserNotFoundException();
        }
        if (request == null
                || request.matchId() == null
                || request.matchId() <= 0
                || request.revisitIntention() == null) {
            throw new ReviewException(ReviewErrorCode.INVALID_INPUT);
        }
    }

    private void validateResolvedParticipants(
            UUID reviewerId,
            MatchParticipationQueryService.MatchParticipation participation
    ) {
        if (participation == null
                || participation.match() == null
                || participation.reviewer() == null
                || participation.reviewee() == null
                || participation.match().getEndedAt() == null
                || participation.reviewer().getId() == null
                || participation.reviewee().getId() == null) {
            throw new ReviewException(ReviewErrorCode.DATA_INCONSISTENT);
        }
        if (!reviewerId.equals(participation.reviewer().getId())
                || reviewerId.equals(participation.reviewee().getId())
                || participation.reviewer().getId().equals(participation.reviewee().getId())) {
            throw new ReviewException(
                    ReviewErrorCode.FORBIDDEN,
                    "자기 자신에게 후기를 작성하거나 다른 작성자로 위장할 수 없습니다."
            );
        }
    }
}
