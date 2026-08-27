package org.example.project2.domain.matching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.domain.matching.dto.BidirectionalMatchScoreSnapshot;
import org.example.project2.global.entity.BaseEntity;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Table(name = "match_proposals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_proposals_request_pair", columnNames = {"request_1_id", "request_2_id"})
}, indexes = {
        @Index(name = "idx_match_proposals_status", columnList = "status"),
        @Index(name = "idx_match_proposals_request_1", columnList = "request_1_id"),
        @Index(name = "idx_match_proposals_request_2", columnList = "request_2_id"),
        @Index(name = "idx_match_proposals_expires_at", columnList = "expires_at")
})
@Entity
@Check(constraints = "request_1_id < request_2_id " +
        "AND (status <> 'MATCHED' OR (request_1_decision = 'ACCEPTED' AND request_2_decision = 'ACCEPTED')) " +
        "AND (status <> 'REJECTED' OR (request_1_decision = 'REJECTED' OR request_2_decision = 'REJECTED'))")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchProposal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_1_id", nullable = false)
    private MatchRequest request1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_2_id", nullable = false)
    private MatchRequest request2;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "request_1_decision", nullable = false, length = 20)
    private MatchProposalDecision request1Decision = MatchProposalDecision.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "request_2_decision", nullable = false, length = 20)
    private MatchProposalDecision request2Decision = MatchProposalDecision.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchProposalStatus status = MatchProposalStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_snapshot", columnDefinition = "jsonb")
    private BidirectionalMatchScoreSnapshot scoreSnapshot;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "request_1_decided_at")
    private Instant request1DecidedAt;

    @Column(name = "request_2_decided_at")
    private Instant request2DecidedAt;

    public static MatchProposal of(
            MatchRequest a,
            MatchRequest b,
            BidirectionalMatchScoreSnapshot scoreSnapshotFromAToB,
            Instant expiresAt
    ) {
        if (a == null || b == null || a.getId() == null || b.getId() == null) {
            throw new IllegalArgumentException("매칭 요청은 null일 수 없으며 ID가 존재해야 합니다.");
        }
        if (a.getId().equals(b.getId())) {
            throw new IllegalArgumentException("동일한 매칭 요청 간에는 제안을 생성할 수 없습니다.");
        }
        validateDifferentOwners(a, b);
        if (expiresAt == null) {
            throw new IllegalArgumentException("후보 제안 만료 시각은 필수입니다.");
        }
        boolean aIsFirst = a.getId() < b.getId();
        MatchRequest r1 = aIsFirst ? a : b;
        MatchRequest r2 = aIsFirst ? b : a;

        return MatchProposal.builder()
                .request1(r1)
                .request2(r2)
                .request1Decision(MatchProposalDecision.PENDING)
                .request2Decision(MatchProposalDecision.PENDING)
                .status(MatchProposalStatus.PENDING)
                .scoreSnapshot(aIsFirst || scoreSnapshotFromAToB == null
                        ? scoreSnapshotFromAToB
                        : scoreSnapshotFromAToB.reversed())
                .expiresAt(expiresAt)
                .build();
    }

    public boolean involvesRequest(Long requestId) {
        if (requestId == null) {
            return false;
        }
        return requestId.equals(request1.getId()) || requestId.equals(request2.getId());
    }

    public MatchRequest getOtherRequest(Long requestId) {
        if (requestId == null) {
            return null;
        }
        if (requestId.equals(request1.getId())) {
            return request2;
        }
        if (requestId.equals(request2.getId())) {
            return request1;
        }
        return null;
    }

    public MatchProposalDecision getDecisionFor(Long requestId) {
        if (requestId == null) {
            return null;
        }
        if (requestId.equals(request1.getId())) {
            return request1Decision;
        }
        if (requestId.equals(request2.getId())) {
            return request2Decision;
        }
        throw new IllegalArgumentException("해당 제안에 참여하지 않은 요청 ID입니다: " + requestId);
    }

    public void decide(Long requestId, MatchProposalDecision decision, Instant now) {
        if (requestId == null) {
            throw new IllegalArgumentException("매칭 요청 ID는 필수입니다.");
        }
        if (decision == null || decision == MatchProposalDecision.PENDING) {
            throw new IllegalArgumentException("후보 제안 결정은 ACCEPTED 또는 REJECTED만 가능합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("후보 제안 응답 시각은 필수입니다.");
        }

        MatchProposalDecision currentDecision;
        if (requestId.equals(request1.getId())) {
            currentDecision = request1Decision;
        } else if (requestId.equals(request2.getId())) {
            currentDecision = request2Decision;
        } else {
            throw new IllegalArgumentException("해당 제안에 참여하지 않은 요청 ID입니다: " + requestId);
        }

        if (currentDecision == decision) {
            return;
        }
        if (currentDecision != MatchProposalDecision.PENDING) {
            throw new IllegalStateException("이미 확정한 후보 제안 결정은 변경할 수 없습니다.");
        }
        if (this.status != MatchProposalStatus.PENDING) {
            throw new IllegalStateException("진행 중인(PENDING) 제안에 대해서만 결정을 내릴 수 있습니다.");
        }
        if (!now.isBefore(this.expiresAt)) {
            this.status = MatchProposalStatus.EXPIRED;
            throw new IllegalStateException("응답 제한 시간이 만료된 제안입니다.");
        }

        if (requestId.equals(request1.getId())) {
            this.request1Decision = decision;
            this.request1DecidedAt = now;
        } else {
            this.request2Decision = decision;
            this.request2DecidedAt = now;
        }

        if (decision == MatchProposalDecision.REJECTED) {
            this.status = MatchProposalStatus.REJECTED;
        }
    }

    public boolean isBothAccepted() {
        return this.request1Decision == MatchProposalDecision.ACCEPTED
                && this.request2Decision == MatchProposalDecision.ACCEPTED;
    }

    public boolean isAnyRejected() {
        return this.request1Decision == MatchProposalDecision.REJECTED
                || this.request2Decision == MatchProposalDecision.REJECTED;
    }

    public boolean isExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후보 제안 만료 확인 시각은 필수입니다.");
        }
        return this.status == MatchProposalStatus.EXPIRED
                || (this.status == MatchProposalStatus.PENDING && !now.isBefore(this.expiresAt));
    }

    public void expire() {
        if (this.status == MatchProposalStatus.EXPIRED) {
            return;
        }
        if (this.status == MatchProposalStatus.PENDING) {
            this.status = MatchProposalStatus.EXPIRED;
            return;
        }
        throw new IllegalStateException("진행 중인 제안만 만료 처리할 수 있습니다. 현재 상태: " + this.status);
    }

    public void cancel() {
        if (this.status == MatchProposalStatus.CANCELLED) {
            return;
        }
        if (this.status == MatchProposalStatus.PENDING) {
            this.status = MatchProposalStatus.CANCELLED;
            return;
        }
        throw new IllegalStateException("진행 중인 제안만 취소할 수 있습니다. 현재 상태: " + this.status);
    }

    public void match() {
        if (this.status != MatchProposalStatus.PENDING) {
            throw new IllegalStateException("진행 중인 제안만 매칭 완료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        if (!isBothAccepted()) {
            throw new IllegalStateException("양쪽 사용자가 모두 수락한 제안만 매칭 완료 처리할 수 있습니다.");
        }
        this.status = MatchProposalStatus.MATCHED;
    }

    private static void validateDifferentOwners(MatchRequest a, MatchRequest b) {
        if (a.getUser() == null || b.getUser() == null) {
            throw new IllegalArgumentException("매칭 요청 소유자는 필수입니다.");
        }
        UUID aUserId = a.getUser().getId();
        UUID bUserId = b.getUser().getId();
        if (aUserId == null || bUserId == null) {
            throw new IllegalArgumentException("저장된 사용자 요청만 후보 제안에 사용할 수 있습니다.");
        }
        if (aUserId.equals(bUserId)) {
            throw new IllegalArgumentException("동일한 사용자에게 후보 제안을 생성할 수 없습니다.");
        }
    }
}
