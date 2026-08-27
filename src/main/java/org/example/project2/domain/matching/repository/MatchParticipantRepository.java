package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {
    List<MatchParticipant> findAllByMatchId(Long matchId);
    List<MatchParticipant> findAllByUserId(UUID userId);
}
