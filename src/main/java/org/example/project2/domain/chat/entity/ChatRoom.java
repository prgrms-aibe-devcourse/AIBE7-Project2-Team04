package org.example.project2.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.matching.entity.Match;

import java.time.Instant;

@Table(name = "chat_rooms")
@Entity
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
}
