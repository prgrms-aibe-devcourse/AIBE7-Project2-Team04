package org.example.project2.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.user.entity.User;

@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_created", columnList = "chat_room_id, created_at DESC"))
@Entity
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
public class ChatMessage extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
