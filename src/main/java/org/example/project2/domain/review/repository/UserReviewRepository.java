package org.example.project2.domain.review.repository;

import org.example.project2.domain.review.entity.RevisitIntention;
import org.example.project2.domain.review.entity.UserReview;
import org.example.project2.domain.review.entity.UserReviewVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserReviewRepository extends JpaRepository<UserReview, Long> {
    boolean existsByMatch_IdAndReviewer_IdAndReviewee_Id(
            Long matchId,
            UUID reviewerId,
            UUID revieweeId
    );

    @Query("""
            SELECT COUNT(r.id) AS validReviewCount,
                   COALESCE(SUM(CASE WHEN r.revisitIntention = :definitelyAgain THEN 1 ELSE 0 END), 0)
                       AS definitelyAgainCount,
                   COALESCE(SUM(CASE WHEN r.revisitIntention = :maybeAgain THEN 1 ELSE 0 END), 0)
                       AS maybeAgainCount,
                   COALESCE(SUM(CASE WHEN r.revisitIntention = :enoughForNow THEN 1 ELSE 0 END), 0)
                       AS enoughForNowCount
            FROM UserReview r
            WHERE r.reviewee.id = :revieweeId
              AND r.visibility = :visibility
            """)
    ReviewScoreAggregateProjection aggregateByRevieweeIdAndVisibility(
            @Param("revieweeId") UUID revieweeId,
            @Param("visibility") UserReviewVisibility visibility,
            @Param("definitelyAgain") RevisitIntention definitelyAgain,
            @Param("maybeAgain") RevisitIntention maybeAgain,
            @Param("enoughForNow") RevisitIntention enoughForNow
    );
}
