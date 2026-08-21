package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Table(name = "match_requests", indexes = {
        @Index(name = "idx_match_requests_user", columnList = "user_id")
})
@Entity
@Check(constraints = "participant_count > 0 AND (search_radius IS NULL OR search_radius > 0) AND reject_count >= 0")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "food_category", nullable = false, length = 100)
    private String foodCategory;

    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    @Column(name = "meal_at", nullable = false)
    private Instant mealAt;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "search_radius")
    private Integer searchRadius;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchRequestStatus status = MatchRequestStatus.WAITING;

    @Builder.Default
    @Column(name = "reject_count", nullable = false)
    private int rejectCount = 0;
}
