package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {
    List<MatchParticipant> findAllByMatchId(Long matchId);

    @Query("""
            SELECT p
            FROM MatchParticipant p
            JOIN FETCH p.user
            WHERE p.match.id = :matchId
            ORDER BY p.id
            """)
    List<MatchParticipant> findAllByMatchIdWithUser(@Param("matchId") Long matchId);

    List<MatchParticipant> findAllByUserId(UUID userId);
}
