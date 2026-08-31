package org.example.project2.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_nickname", columnList = "nickname", unique = true)
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_users_provider_provider_id",
        columnNames = {"provider", "provider_id"}
))
@Entity
@Check(constraints = "(provider = 'LOCAL' AND password_hash IS NOT NULL AND provider_id IS NULL) OR " +
        "(provider IN ('KAKAO', 'GOOGLE') AND password_hash IS NULL AND provider_id IS NOT NULL)")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Builder.Default
    @ColumnDefault("'LOCAL'")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(nullable = false, unique = true, length = 100)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(name = "profile_image_key", columnDefinition = "text")
    private String profileImageKey;

    @Column(columnDefinition = "text")
    private String description;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_food_preferences",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_food_preference",
                    columnNames = {"user_id", "food_category"}
            ),
            indexes = @Index(name = "idx_user_food_preferences_category", columnList = "food_category")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", nullable = false, length = 50)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<FoodCategory> foodPreferences = new HashSet<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Builder.Default
    @ColumnDefault("'NOT_STARTED'")
    @Enumerated(EnumType.STRING)
    @Column(name = "personality_onboarding_status", nullable = false, length = 20)
    private PersonalityOnboardingStatus personalityOnboardingStatus = PersonalityOnboardingStatus.NOT_STARTED;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void skipPersonalityOnboarding() {
        personalityOnboardingStatus = PersonalityOnboardingStatus.SKIPPED;
    }

    public void completePersonalityOnboarding() {
        personalityOnboardingStatus = PersonalityOnboardingStatus.COMPLETED;
    }

    public void resetPersonalityOnboarding() {
        personalityOnboardingStatus = PersonalityOnboardingStatus.NOT_STARTED;
    }

    public void replaceFoodPreferences(Set<FoodCategory> foodPreferences) {
        this.foodPreferences.clear();
        this.foodPreferences.addAll(foodPreferences);
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl.isBlank() ? null : profileImageUrl;
        }
    }

    public void updateProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        String uuid = java.util.UUID.randomUUID().toString();
        this.email = "withdrawn_" + uuid + "@withdrawn.com";
        if (this.providerId != null) {
            this.providerId = "withdrawn_" + uuid;
        }
        this.nickname = "탈퇴회원_" + uuid.substring(0, 8);
        this.profileImageUrl = null;
        this.profileImageKey = null;
    }
}
