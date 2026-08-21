package org.example.project2.domain.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.LongIdEntity;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Table(name = "match_post_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_match_post_participant", columnNames = {"match_post_id", "user_id"}),
        indexes = {
                @Index(name = "idx_match_post_participants_post", columnList = "match_post_id"),
                @Index(name = "idx_match_post_participants_user", columnList = "user_id")
        })
@Entity
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchPostParticipant extends LongIdEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_post_id", nullable = false)
    private MatchPost matchPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchPostParticipantStatus status = MatchPostParticipantStatus.PENDING;

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
