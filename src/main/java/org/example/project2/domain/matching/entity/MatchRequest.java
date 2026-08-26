package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.global.entity.BaseEntity;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Table(name = "match_requests", indexes = {
        @Index(name = "idx_match_requests_user", columnList = "user_id"),
        @Index(name = "idx_match_requests_region_status", columnList = "region_code, status")
})
@Entity
@Check(constraints = "(search_radius IS NULL OR search_radius > 0) AND reject_count >= 0")
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

    @Column(name = "meal_at", nullable = false)
    private Instant mealAt;

    @Column(name = "region_code", nullable = false, length = 5)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "search_radius")
    private Integer searchRadius;

    @Column(name = "desired_personality_text", columnDefinition = "text")
    private String desiredPersonalityText;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "match_request_desired_personality_tags",
            joinColumns = @JoinColumn(name = "match_request_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_match_request_desired_personality_tag",
                    columnNames = {"match_request_id", "tag_code"}
            ),
            indexes = @Index(name = "idx_match_request_desired_personality_tags_tag", columnList = "tag_code")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 50)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<PersonalityTag> desiredPersonalityTags = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preference_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> preferenceSnapshot;

    @Column(name = "matching_formula_version", length = 50)
    private String matchingFormulaVersion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchRequestStatus status = MatchRequestStatus.WAITING;

    @Builder.Default
    @Column(name = "reject_count", nullable = false)
    private int rejectCount = 0;
}
