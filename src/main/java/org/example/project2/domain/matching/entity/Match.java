package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.example.project2.domain.recruitment.entity.MatchPost;
import org.hibernate.annotations.Check;
import java.time.Instant;

@Table(name = "matches", indexes = {
        @Index(name = "idx_matches_request", columnList = "match_request_id"),
        @Index(name = "idx_matches_post", columnList = "match_post_id")
})
@Entity
@Check(constraints = "(match_type = 'REALTIME' AND match_request_id IS NOT NULL AND match_post_id IS NULL) OR " +
        "(match_type = 'POST' AND match_post_id IS NOT NULL AND match_request_id IS NULL)")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Match extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_request_id")
    private MatchRequest matchRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_post_id")
    private MatchPost matchPost;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.MATCHED;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;
}
