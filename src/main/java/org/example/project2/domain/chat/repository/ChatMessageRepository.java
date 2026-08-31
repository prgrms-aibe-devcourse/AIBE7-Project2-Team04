package org.example.project2.domain.chat.repository;

import org.example.project2.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("SELECT cm FROM ChatMessage cm JOIN FETCH cm.sender WHERE cm.chatRoom.id = :roomId AND (:cursor IS NULL OR cm.id < :cursor) ORDER BY cm.id DESC")
    List<ChatMessage> findMessagesWithCursor(@Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);

    List<ChatMessage> findByChatRoom_IdOrderByIdAsc(Long chatRoomId);
}
