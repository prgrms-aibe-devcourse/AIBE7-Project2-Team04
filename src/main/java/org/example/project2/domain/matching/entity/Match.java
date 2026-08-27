package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.hibernate.annotations.Check;
import java.time.Instant;
import java.util.UUID;

@Table(name = "matches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_matches_request_pair", columnNames = {"request_1_id", "request_2_id"})
})
@Entity
@Check(constraints = "request_1_id < request_2_id")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Match extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_1_id", nullable = false, unique = true)
    private MatchRequest request1;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_2_id", nullable = false, unique = true)
    private MatchRequest request2;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.MATCHED;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    public static Match of(MatchRequest a, MatchRequest b, Instant matchedAt) {
        if (a == null || b == null || a.getId() == null || b.getId() == null) {
            throw new IllegalArgumentException("매칭 요청은 null일 수 없으며 ID가 존재해야 합니다.");
        }
        if (a.getId().equals(b.getId())) {
            throw new IllegalArgumentException("동일한 매칭 요청 간에는 매칭을 생성할 수 없습니다.");
        }
        validateDifferentOwners(a, b);
        if (matchedAt == null) {
            throw new IllegalArgumentException("매칭 성사 시각은 필수입니다.");
        }
        MatchRequest r1 = a.getId() < b.getId() ? a : b;
        MatchRequest r2 = a.getId() < b.getId() ? b : a;
        return Match.builder()
                .request1(r1)
                .request2(r2)
                .status(MatchStatus.MATCHED)
                .matchedAt(matchedAt)
                .build();
    }

    private static void validateDifferentOwners(MatchRequest a, MatchRequest b) {
        if (a.getUser() == null || b.getUser() == null) {
            throw new IllegalArgumentException("매칭 요청 소유자는 필수입니다.");
        }
        UUID aUserId = a.getUser().getId();
        UUID bUserId = b.getUser().getId();
        if (aUserId == null || bUserId == null) {
            throw new IllegalArgumentException("저장된 사용자 요청만 매칭에 사용할 수 있습니다.");
        }
        if (aUserId.equals(bUserId)) {
            throw new IllegalArgumentException("동일한 사용자의 요청끼리는 매칭할 수 없습니다.");
        }
    }
}
