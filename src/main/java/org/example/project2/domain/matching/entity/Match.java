package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.hibernate.annotations.Check;
import java.time.Instant;

@Table(name = "matches")
@Entity
@Check(constraints = "requester_request_id <> candidate_request_id")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Match extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_request_id", nullable = false, unique = true)
    private MatchRequest requesterRequest;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_request_id", nullable = false, unique = true)
    private MatchRequest candidateRequest;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.MATCHED;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;
}
