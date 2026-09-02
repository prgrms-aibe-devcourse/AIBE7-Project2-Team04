package org.example.project2.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.CreatedEntity;
import org.example.project2.domain.user.entity.User;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Check;
import org.locationtech.jts.geom.Point;

@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_created", columnList = "chat_room_id, created_at DESC"))
@Entity
@Check(constraints = "(message_type = 'TEXT' AND provider_place_id IS NULL AND place_name IS NULL " +
        "AND place_category IS NULL AND place_address IS NULL AND place_location IS NULL AND place_url IS NULL) " +
        "OR (message_type = 'PLACE' AND provider_place_id IS NOT NULL AND place_name IS NOT NULL " +
        "AND place_category IS NOT NULL AND place_address IS NOT NULL AND place_location IS NOT NULL " +
        "AND place_url IS NOT NULL)")
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'TEXT'")
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.TEXT;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "provider_place_id", length = 30)
    private String providerPlaceId;

    @Column(name = "place_name", length = 200)
    private String placeName;

    @Column(name = "place_category", length = 200)
    private String placeCategory;

    @Column(name = "place_address", length = 500)
    private String placeAddress;

    @Column(name = "place_location", columnDefinition = "geography(Point,4326)")
    private Point placeLocation;

    @Column(name = "place_url", length = 300)
    private String placeUrl;
}
