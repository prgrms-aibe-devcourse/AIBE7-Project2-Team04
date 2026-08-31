package org.example.project2.domain.review.service;

import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.exception.MatchNotCompletedException;
import org.example.project2.domain.matching.exception.NotMatchParticipantException;
import org.example.project2.domain.matching.service.MatchParticipationQueryService;
import org.example.project2.domain.review.dto.ReviewCreateRequest;
import org.example.project2.domain.review.dto.ReviewCreateResponse;
import org.example.project2.domain.review.entity.ImpressionTag;
import org.example.project2.domain.review.entity.RevisitIntention;
import org.example.project2.domain.review.entity.UserReview;
import org.example.project2.domain.review.exception.AuthenticatedReviewUserNotFoundException;
import org.example.project2.domain.review.exception.ReviewErrorCode;
import org.example.project2.domain.review.exception.ReviewException;
import org.example.project2.domain.review.event.ReviewAuditEventPublisher;
import org.example.project2.domain.review.repository.UserReviewRepository;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock MatchParticipationQueryService matchParticipationQueryService;
    @Mock UserReviewRepository userReviewRepository;
    @Mock ReviewAuditEventPublisher reviewAuditEventPublisher;

    private ReviewCommandService service;
    private UUID reviewerId;
    private UUID revieweeId;
    private Match match;
    private User reviewer;
    private User reviewee;

    @BeforeEach
    void setUp() {
        service = new ReviewCommandService(
                matchParticipationQueryService,
                userReviewRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                reviewAuditEventPublisher
        );
        reviewerId = UUID.randomUUID();
        revieweeId = UUID.randomUUID();
        reviewer = user(reviewerId);
        reviewee = user(revieweeId);
        match = org.mockito.Mockito.mock(Match.class);
        lenient().when(match.getEndedAt()).thenReturn(NOW.minusSeconds(3600));
        lenient().when(matchParticipationQueryService.findCompletedParticipation(301L, reviewerId))
                .thenReturn(new MatchParticipationQueryService.MatchParticipation(match, reviewer, reviewee));
    }

    @Test
    void createsReviewWithAuthenticatedReviewerAndResolvedReviewee() {
        UserReview saved = org.mockito.Mockito.mock(UserReview.class);
        when(saved.getId()).thenReturn(901L);
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);
        when(userReviewRepository.saveAndFlush(any(UserReview.class))).thenReturn(saved);

        ReviewCreateResponse response = service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.DEFINITELY_AGAIN, ImpressionTag.PUNCTUAL)
        );

        assertThat(response.reviewId()).isEqualTo(901L);
        assertThat(response.submittedAt()).isEqualTo(NOW);
        ArgumentCaptor<UserReview> captor = ArgumentCaptor.forClass(UserReview.class);
        verify(userReviewRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMatch()).isSameAs(match);
        assertThat(captor.getValue().getReviewer()).isSameAs(reviewer);
        assertThat(captor.getValue().getReviewee()).isSameAs(reviewee);
        assertThat(captor.getValue().getRevisitIntention()).isEqualTo(RevisitIntention.DEFINITELY_AGAIN);
        assertThat(captor.getValue().getImpressionTag()).isEqualTo(ImpressionTag.PUNCTUAL);
        verify(reviewAuditEventPublisher).reviewSubmitted(901L, 301L, reviewerId, revieweeId, NOW);
    }

    @Test
    void acceptsEveryOptionalTagAndAnOmittedTag() {
        UserReview saved = org.mockito.Mockito.mock(UserReview.class);
        when(saved.getId()).thenReturn(901L);
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);
        when(userReviewRepository.saveAndFlush(any(UserReview.class))).thenReturn(saved);

        for (ImpressionTag tag : ImpressionTag.values()) {
            service.create(
                    reviewerId,
                    new ReviewCreateRequest(301L, RevisitIntention.DEFINITELY_AGAIN, tag)
            );
        }
        service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.DEFINITELY_AGAIN, null)
        );

        ArgumentCaptor<UserReview> captor = ArgumentCaptor.forClass(UserReview.class);
        verify(userReviewRepository, times(ImpressionTag.values().length + 1)).saveAndFlush(captor.capture());
        List<UserReview> submittedReviews = captor.getAllValues();
        assertThat(submittedReviews).hasSize(ImpressionTag.values().length + 1);
        for (int index = 0; index < ImpressionTag.values().length; index++) {
            assertThat(submittedReviews.get(index).getImpressionTag()).isEqualTo(ImpressionTag.values()[index]);
        }
        assertThat(submittedReviews.get(ImpressionTag.values().length).getImpressionTag()).isNull();
    }

    @Test
    void allowsSubmissionExactlyBeforeSevenDayDeadline() {
        when(match.getEndedAt()).thenReturn(NOW.minusSeconds(7 * 24 * 60 * 60L).plusNanos(1));
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);
        UserReview saved = org.mockito.Mockito.mock(UserReview.class);
        when(saved.getId()).thenReturn(901L);
        when(userReviewRepository.saveAndFlush(any(UserReview.class))).thenReturn(saved);

        assertThat(service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ).reviewId()).isEqualTo(901L);
    }

    @Test
    void rejectsSubmissionAtSevenDayDeadline() {
        when(match.getEndedAt()).thenReturn(NOW.minusSeconds(7 * 24 * 60 * 60L));
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_PERIOD_EXPIRED);
        verify(reviewAuditEventPublisher).periodExpired(301L, reviewerId, revieweeId);
    }

    @Test
    void mapsIncompleteMatchToReviewNotAvailable() {
        when(matchParticipationQueryService.findCompletedParticipation(301L, reviewerId))
                .thenThrow(new MatchNotCompletedException());

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_AVAILABLE);
    }

    @Test
    void hidesWhetherGuessedMatchIdExistsFromNonParticipant() {
        when(matchParticipationQueryService.findCompletedParticipation(301L, reviewerId))
                .thenThrow(new NotMatchParticipantException());

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsSelfReviewEvenIfResolvedParticipantDataIsTamperedWith() {
        when(matchParticipationQueryService.findCompletedParticipation(301L, reviewerId))
                .thenReturn(new MatchParticipationQueryService.MatchParticipation(match, reviewer, reviewer));

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsDuplicateBeforeWritingAnotherRow() {
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
        verify(reviewAuditEventPublisher).duplicateRejected(301L, reviewerId, revieweeId);
    }

    @Test
    void mapsConcurrentUniqueViolationToDuplicateError() {
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);
        when(userReviewRepository.saveAndFlush(any(UserReview.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
        verify(reviewAuditEventPublisher).duplicateRejected(301L, reviewerId, revieweeId);
    }

    @Test
    void allowsOnlyOneConcurrentSubmissionWhenTheDatabaseUniqueKeyWins() throws Exception {
        UserReview saved = org.mockito.Mockito.mock(UserReview.class);
        when(saved.getId()).thenReturn(901L);
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);

        AtomicBoolean rowStored = new AtomicBoolean();
        when(userReviewRepository.saveAndFlush(any(UserReview.class))).thenAnswer(invocation -> {
            if (rowStored.compareAndSet(false, true)) {
                return saved;
            }
            throw new DataIntegrityViolationException("uk_user_review_match_reviewer_reviewee");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시성 테스트 시작 신호를 받지 못했습니다.");
                    }
                    try {
                        return service.create(
                                reviewerId,
                                new ReviewCreateRequest(301L, RevisitIntention.MAYBE_AGAIN, null)
                        );
                    } catch (ReviewException exception) {
                        return exception;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> outcomes = futures.stream().map(future -> {
                try {
                    return future.get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError("동시성 제출 테스트가 예기치 않게 실패했습니다.", exception);
                }
            }).toList();

            assertThat(outcomes.stream().filter(ReviewCreateResponse.class::isInstance).count()).isEqualTo(1);
            List<ReviewException> conflicts = outcomes.stream()
                    .filter(ReviewException.class::isInstance)
                    .map(ReviewException.class::cast)
                    .toList();
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
            verify(userReviewRepository, times(2)).saveAndFlush(any(UserReview.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void letsBothParticipantsReviewEachOtherOnce() {
        when(matchParticipationQueryService.findCompletedParticipation(301L, revieweeId))
                .thenReturn(new MatchParticipationQueryService.MatchParticipation(match, reviewee, reviewer));
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, reviewerId, revieweeId))
                .thenReturn(false);
        when(userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(301L, revieweeId, reviewerId))
                .thenReturn(false);
        UserReview firstSaved = org.mockito.Mockito.mock(UserReview.class);
        UserReview secondSaved = org.mockito.Mockito.mock(UserReview.class);
        when(firstSaved.getId()).thenReturn(901L);
        when(secondSaved.getId()).thenReturn(902L);
        when(userReviewRepository.saveAndFlush(any(UserReview.class))).thenReturn(firstSaved, secondSaved);

        service.create(
                reviewerId,
                new ReviewCreateRequest(301L, RevisitIntention.DEFINITELY_AGAIN, ImpressionTag.PUNCTUAL)
        );
        service.create(
                revieweeId,
                new ReviewCreateRequest(301L, RevisitIntention.ENOUGH_FOR_NOW, ImpressionTag.CONSIDERATE)
        );

        ArgumentCaptor<UserReview> captor = ArgumentCaptor.forClass(UserReview.class);
        verify(userReviewRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserReview::getReviewer)
                .containsExactly(reviewer, reviewee);
        assertThat(captor.getAllValues()).extracting(UserReview::getReviewee)
                .containsExactly(reviewee, reviewer);
    }

    @Test
    void requiresAuthenticatedUserAndRequiredAnswer() {
        assertThatThrownBy(() -> service.create(null, null))
                .isInstanceOf(AuthenticatedReviewUserNotFoundException.class);
        assertThatThrownBy(() -> service.create(
                reviewerId,
                new ReviewCreateRequest(301L, null, null)
        ))
                .isInstanceOf(ReviewException.class)
                .extracting(exception -> ((ReviewException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.INVALID_INPUT);
    }

    private User user(UUID id) {
        return User.builder()
                .id(id)
                .email(id + "@test.com")
                .nickname("사용자-" + id.toString().substring(0, 8))
                .build();
    }
}
