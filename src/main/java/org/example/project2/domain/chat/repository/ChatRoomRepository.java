package org.example.project2.domain.chat.repository;

import org.example.project2.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 주어진 채팅방에 해당 유저가 match_participants 기준으로 참여자인지 확인합니다.
     * chat_rooms → matches → match_participants 경로로 조인합니다.
     */
    @Query("""
            SELECT COUNT(mp) > 0
            FROM ChatRoom cr
            JOIN cr.match m
            JOIN MatchParticipant mp ON mp.match = m
            WHERE cr.id = :roomId AND mp.user.id = :userId
            """)
    boolean existsParticipantByRoomIdAndUserId(@Param("roomId") Long roomId,
                                               @Param("userId") UUID userId);

    Optional<ChatRoom> findByMatchId(Long matchId);
}
