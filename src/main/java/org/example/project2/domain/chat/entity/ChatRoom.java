package org.example.project2.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.matching.entity.Match;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Table(name = "chat_rooms")
@Entity
@Check(constraints = "((status = 'ACTIVE' AND closed_at IS NULL) " +
        "OR (status = 'CLOSED' AND closed_at IS NOT NULL))")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
public class ChatRoom extends CreatedEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status = ChatRoomStatus.ACTIVE;

    @Column(name = "closed_at")
    private Instant closedAt;

    public boolean isActive() {
        return this.status == ChatRoomStatus.ACTIVE;
    }

    public void close(Instant closedAt) {
        if (this.status == ChatRoomStatus.CLOSED) {
            return;
        }
        if (this.status != ChatRoomStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태의 채팅방만 종료할 수 있습니다. 현재 상태: " + this.status);
        }
        if (closedAt == null) {
            throw new IllegalArgumentException("채팅방 종료 시각은 필수입니다.");
        }
        this.status = ChatRoomStatus.CLOSED;
        this.closedAt = closedAt;
    }

    @PrePersist
    @PreUpdate
    private void validateStatusState() {
        if (this.status == null) {
            throw new IllegalStateException("채팅방 상태는 필수입니다.");
        }
        if (this.status == ChatRoomStatus.ACTIVE && this.closedAt != null) {
            throw new IllegalStateException("활성 채팅방에는 종료 시각을 설정할 수 없습니다.");
        }
        if (this.status == ChatRoomStatus.CLOSED && this.closedAt == null) {
            throw new IllegalStateException("종료된 채팅방에는 종료 시각이 필요합니다.");
        }
    }
}
