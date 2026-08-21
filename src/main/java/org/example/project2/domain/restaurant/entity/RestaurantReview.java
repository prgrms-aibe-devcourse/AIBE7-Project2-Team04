package org.example.project2.domain.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;

@Table(name = "restaurant_reviews", indexes = {
        @Index(name = "idx_restaurant_reviews_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_restaurant_reviews_match", columnList = "match_id")
})
@Entity
@Check(constraints = "rating BETWEEN 1 AND 5")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class RestaurantReview extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
