package org.example.project2.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Table(name = "user_matching_preferences")
@Entity
@EntityListeners(AuditingEntityListener.class)
@Check(constraints = "importance BETWEEN 0 AND 5")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserMatchingPreference implements Persistable<UserMatchingPreferenceId> {
    @EmbeddedId
    private UserMatchingPreferenceId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false)
    private short importance;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_mode", nullable = false, length = 20)
    private PreferenceMode preferenceMode;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public UserMatchingPreferenceId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        this.newEntity = false;
    }

    public static UserMatchingPreference of(
            User user,
            PersonalityDimension dimension,
            short importance,
            PreferenceMode preferenceMode
    ) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("저장된 사용자는 필수입니다.");
        }
        validate(importance, preferenceMode);
        return UserMatchingPreference.builder()
                .id(UserMatchingPreferenceId.of(user.getId(), dimension))
                .user(user)
                .importance(importance)
                .preferenceMode(preferenceMode)
                .build();
    }

    private static void validate(short importance, PreferenceMode preferenceMode) {
        if (importance < 0 || importance > 5) {
            throw new IllegalArgumentException("성향 중요도는 0 이상 5 이하여야 합니다.");
        }
        if (preferenceMode == null) {
            throw new IllegalArgumentException("성향 선호 방식은 필수입니다.");
        }
    }
}
