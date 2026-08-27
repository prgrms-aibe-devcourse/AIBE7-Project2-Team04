package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.domain.matching.dto.scoring.MatchingPreferenceSnapshot;
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
import java.util.Set;

@Table(name = "match_requests", indexes = {
        @Index(name = "idx_match_requests_user", columnList = "user_id"),
        @Index(name = "idx_match_requests_region_status", columnList = "region_code, status")
})
@Entity
@Check(constraints = "(search_radius IS NULL OR search_radius > 0) AND reject_count >= 0 " +
        "AND ((desired_personality_embedding IS NULL AND embedding_model IS NULL " +
        "AND embedding_version IS NULL AND embedded_at IS NULL) " +
        "OR (desired_personality_embedding IS NOT NULL AND embedding_model IS NOT NULL " +
        "AND embedding_version IS NOT NULL AND embedded_at IS NOT NULL))")
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

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Getter(AccessLevel.NONE)
    @Column(name = "desired_personality_embedding", columnDefinition = "vector(1536)")
    private float[] desiredPersonalityEmbedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_version", length = 50)
    private String embeddingVersion;

    @Column(name = "embedded_at")
    private Instant embeddedAt;

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
    private MatchingPreferenceSnapshot preferenceSnapshot;

    @Column(name = "matching_formula_version", length = 50)
    private String matchingFormulaVersion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchRequestStatus status = MatchRequestStatus.WAITING;

    @Builder.Default
    @Column(name = "reject_count", nullable = false)
    private int rejectCount = 0;

    public static MatchRequest create(
            User user,
            String foodCategory,
            Instant mealAt,
            String regionCode,
            String regionName,
            String locationName,
            Point location,
            int searchRadius,
            Set<PersonalityTag> desiredPersonalityTags,
            String desiredPersonalityText,
            MatchingPreferenceSnapshot preferenceSnapshot,
            String matchingFormulaVersion
    ) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("저장된 사용자는 필수입니다.");
        }
        if (foodCategory == null || foodCategory.isBlank()) {
            throw new IllegalArgumentException("음식 카테고리는 필수입니다.");
        }
        if (mealAt == null) {
            throw new IllegalArgumentException("희망 식사 일시는 필수입니다.");
        }
        if (regionCode == null || regionCode.isBlank() || regionName == null || regionName.isBlank()) {
            throw new IllegalArgumentException("행정구역 코드와 표시명은 필수입니다.");
        }
        if (location == null || location.getSRID() != 4326) {
            throw new IllegalArgumentException("매칭 장소는 SRID 4326 Point여야 합니다.");
        }
        if (searchRadius <= 0) {
            throw new IllegalArgumentException("탐색 반경은 양수여야 합니다.");
        }
        if (desiredPersonalityTags == null || desiredPersonalityTags.size() < 3
                || desiredPersonalityTags.size() > 5) {
            throw new IllegalArgumentException("원하는 상대 성향 태그는 3개 이상 5개 이하여야 합니다.");
        }
        if (matchingFormulaVersion == null || matchingFormulaVersion.isBlank()) {
            throw new IllegalArgumentException("매칭 산식 버전은 필수입니다.");
        }
        return MatchRequest.builder()
                .user(user)
                .foodCategory(foodCategory.strip())
                .mealAt(mealAt)
                .regionCode(regionCode.strip())
                .regionName(regionName.strip())
                .locationName(normalize(locationName))
                .location(location)
                .searchRadius(searchRadius)
                .desiredPersonalityTags(new HashSet<>(desiredPersonalityTags))
                .desiredPersonalityText(normalize(desiredPersonalityText))
                .preferenceSnapshot(preferenceSnapshot)
                .matchingFormulaVersion(matchingFormulaVersion.strip())
                .status(MatchRequestStatus.WAITING)
                .rejectCount(0)
                .build();
    }
    public void startConfirming() {
        if (this.status != MatchRequestStatus.WAITING) {
            throw new IllegalStateException("WAITING 상태의 요청만 제안 확인(CONFIRMING)으로 전환될 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = MatchRequestStatus.CONFIRMING;
    }

    public void returnToWaiting() {
        if (this.status != MatchRequestStatus.CONFIRMING) {
            throw new IllegalStateException("CONFIRMING 상태의 요청만 대기(WAITING)로 복귀할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = MatchRequestStatus.WAITING;
        this.rejectCount++;
    }

    public void match() {
        if (this.status != MatchRequestStatus.CONFIRMING) {
            throw new IllegalStateException("CONFIRMING 상태의 요청만 MATCHED로 전환될 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = MatchRequestStatus.MATCHED;
    }

    public void cancel() {
        if (this.status == MatchRequestStatus.CANCELLED) {
            return;
        }
        if (this.status != MatchRequestStatus.WAITING && this.status != MatchRequestStatus.CONFIRMING) {
            throw new IllegalStateException("대기 또는 제안 확인 중인 요청만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = MatchRequestStatus.CANCELLED;
    }

    public void expire() {
        if (this.status == MatchRequestStatus.EXPIRED) {
            return;
        }
        if (this.status != MatchRequestStatus.WAITING && this.status != MatchRequestStatus.CONFIRMING) {
            throw new IllegalStateException("대기 또는 제안 확인 중인 요청만 만료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = MatchRequestStatus.EXPIRED;
    }

    public boolean isWaiting() {
        return this.status == MatchRequestStatus.WAITING;
    }

    public boolean isConfirming() {
        return this.status == MatchRequestStatus.CONFIRMING;
    }

    public boolean isMatched() {
        return this.status == MatchRequestStatus.MATCHED;
    }

    public boolean isTerminal() {
        return this.status == MatchRequestStatus.MATCHED
                || this.status == MatchRequestStatus.CANCELLED
                || this.status == MatchRequestStatus.EXPIRED;
    }

    public void updateDesiredPersonalityEmbedding(float[] embedding, String model, String version, Instant embeddedAt) {
        validateEmbedding(embedding, model, version, embeddedAt);
        this.desiredPersonalityEmbedding = embedding.clone();
        this.embeddingModel = model.trim();
        this.embeddingVersion = version.trim();
        this.embeddedAt = embeddedAt;
    }

    public float[] getDesiredPersonalityEmbedding() {
        return desiredPersonalityEmbedding == null ? null : desiredPersonalityEmbedding.clone();
    }

    public void clearDesiredPersonalityEmbedding() {
        this.desiredPersonalityEmbedding = null;
        this.embeddingModel = null;
        this.embeddingVersion = null;
        this.embeddedAt = null;
    }

    @PrePersist
    @PreUpdate
    private void validateEmbeddingState() {
        boolean allNull = desiredPersonalityEmbedding == null
                && embeddingModel == null
                && embeddingVersion == null
                && embeddedAt == null;
        boolean allPresent = desiredPersonalityEmbedding != null
                && embeddingModel != null
                && embeddingVersion != null
                && embeddedAt != null;
        if (!allNull && !allPresent) {
            throw new IllegalStateException("희망 설명 임베딩과 모델명, 버전, 생성 시각은 모두 함께 설정되어야 합니다.");
        }
        if (allPresent) {
            validateEmbedding(desiredPersonalityEmbedding, embeddingModel, embeddingVersion, embeddedAt);
        }
    }

    private static void validateEmbedding(float[] embedding, String model, String version, Instant embeddedAt) {
        if (embedding == null || embedding.length != 1536) {
            throw new IllegalArgumentException("희망 설명 임베딩은 정확히 1536차원이어야 합니다.");
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("희망 설명 임베딩에는 유한한 값만 포함할 수 있습니다.");
            }
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("희망 설명 임베딩 모델명은 필수입니다.");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("희망 설명 임베딩 버전은 필수입니다.");
        }
        if (embeddedAt == null) {
            throw new IllegalArgumentException("희망 설명 임베딩 생성 시각은 필수입니다.");
        }
    }
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
