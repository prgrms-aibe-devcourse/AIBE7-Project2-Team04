package org.example.project2.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Table(name = "user_location_preferences")
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserLocationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // 연관 관계가 있는 대상 엔티티(User)의 기본 키(PK)를 현재 엔티티의 기본 키(PK)이자 외래 키(FK)로 매핑하여 공유 식별자 관계를 맺어줍니다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "region_code", nullable = false, length = 5)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    @Builder.Default
    @Column(name = "location_service_consent", nullable = false)
    private boolean locationServiceConsent = false;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void update(String regionCode, String regionName, boolean locationServiceConsent) {
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.locationServiceConsent = locationServiceConsent;
    }
}
