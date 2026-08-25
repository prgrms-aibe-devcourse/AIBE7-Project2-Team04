package org.example.project2.domain.personality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Table(name = "user_personality_profiles")
@Entity
@EntityListeners(AuditingEntityListener.class)
@Check(constraints = "conversation_level BETWEEN 0 AND 100 " +
        "AND meal_pace BETWEEN 0 AND 100 " +
        "AND planning_style BETWEEN 0 AND 100 " +
        "AND novelty_preference BETWEEN 0 AND 100")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserPersonalityProfile {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "questionnaire_version", nullable = false, length = 50)
    private String questionnaireVersion;

    @Column(name = "conversation_level", nullable = false)
    private short conversationLevel;

    @Column(name = "meal_pace", nullable = false)
    private short mealPace;

    @Column(name = "planning_style", nullable = false)
    private short planningStyle;

    @Column(name = "novelty_preference", nullable = false)
    private short noveltyPreference;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_personality_tags",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_personality_tag",
                    columnNames = {"user_id", "tag_code"}
            ),
            indexes = @Index(name = "idx_user_personality_tags_tag", columnList = "tag_code")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 50)
    private Set<PersonalityTag> styleTags = new HashSet<>();

    @Builder.Default
    @Column(name = "ai_analysis_consent", nullable = false)
    private boolean aiAnalysisConsent = false;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
