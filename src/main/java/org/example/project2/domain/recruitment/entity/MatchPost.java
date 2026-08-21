package org.example.project2.domain.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.example.project2.domain.restaurant.entity.Restaurant;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Table(name = "match_posts", indexes = {
        @Index(name = "idx_match_posts_user", columnList = "user_id"),
        @Index(name = "idx_match_posts_restaurant", columnList = "restaurant_id")
})
@Entity
@Check(constraints = "max_participants > 0 AND current_participants BETWEEN 1 AND max_participants")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchPost extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "food_category", nullable = false, length = 100)
    private String foodCategory;

    @Column(name = "meal_at", nullable = false)
    private Instant mealAt;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Builder.Default
    @Column(name = "current_participants", nullable = false)
    private int currentParticipants = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_type", nullable = false, length = 20)
    private MatchPostJoinType joinType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchPostStatus status = MatchPostStatus.RECRUITING;
}
