package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m WHERE m.request1.id = :requestId OR m.request2.id = :requestId")
    Optional<Match> findByRequestId(@Param("requestId") Long requestId);

    @Query("SELECT m FROM Match m WHERE m.request1.id = :request1Id AND m.request2.id = :request2Id")
    Optional<Match> findByRequestPair(
            @Param("request1Id") Long request1Id,
            @Param("request2Id") Long request2Id
    );

    @Query("""
            SELECT m
            FROM Match m
            JOIN FETCH m.request1 r1
            JOIN FETCH r1.user
            JOIN FETCH m.request2 r2
            JOIN FETCH r2.user
            WHERE r1.user.id = :userId OR r2.user.id = :userId
            ORDER BY m.matchedAt DESC, m.id DESC
            """)
    List<Match> findLatestByParticipantUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM Match m
            JOIN FETCH m.request1 r1
            JOIN FETCH r1.user u1
            JOIN FETCH m.request2 r2
            JOIN FETCH r2.user u2
            WHERE u1.id = :userId OR u2.id = :userId
            ORDER BY m.matchedAt DESC, m.id DESC
            """)
    List<Match> findHistoryByParticipantUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM Match m
            JOIN FETCH m.request1 r1
            JOIN FETCH r1.user
            JOIN FETCH m.request2 r2
            JOIN FETCH r2.user
            WHERE m.id = :matchId
              AND (r1.user.id = :userId OR r2.user.id = :userId)
            """)
    Optional<Match> findByIdAndParticipantUserId(
            @Param("matchId") Long matchId,
            @Param("userId") UUID userId
    );
}
