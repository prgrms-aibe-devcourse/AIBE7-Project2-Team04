package org.example.project2.domain.review.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;

@Table(name = "user_reviews", indexes = {
        @Index(name = "idx_user_reviews_match", columnList = "match_id"),
        @Index(name = "idx_user_reviews_reviewer", columnList = "reviewer_id"),
        @Index(name = "idx_user_reviews_reviewee", columnList = "reviewee_id")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_user_review_match_reviewer_reviewee",
        columnNames = {"match_id", "reviewer_id", "reviewee_id"}
))
@Entity
@Check(constraints = "reviewer_id <> reviewee_id "
        + "AND revisit_intention IN ('DEFINITELY_AGAIN', 'MAYBE_AGAIN', 'ENOUGH_FOR_NOW') "
        + "AND (impression_tag IS NULL OR impression_tag IN "
        + "('PUNCTUAL', 'COMFORTABLE_CONVERSATION', 'CONSIDERATE', 'ACTIVE_PARTICIPATION')) "
        + "AND visibility IN ('PUBLIC', 'PRIVATE')")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserReview extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Enumerated(EnumType.STRING)
    @Column(name = "revisit_intention", nullable = false, length = 30)
    private RevisitIntention revisitIntention;

    @Enumerated(EnumType.STRING)
    @Column(name = "impression_tag", length = 30)
    private ImpressionTag impressionTag;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserReviewVisibility visibility = UserReviewVisibility.PUBLIC;
}
