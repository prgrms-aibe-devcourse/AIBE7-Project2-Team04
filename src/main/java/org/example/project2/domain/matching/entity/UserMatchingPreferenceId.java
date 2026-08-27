package org.example.project2.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project2.domain.personality.entity.PersonalityDimension;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMatchingPreferenceId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PersonalityDimension dimension;

    public static UserMatchingPreferenceId of(UUID userId, PersonalityDimension dimension) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("성향 차원은 필수입니다.");
        }
        return new UserMatchingPreferenceId(userId, dimension);
    }
}
