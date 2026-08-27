package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m WHERE m.request1.id = :requestId OR m.request2.id = :requestId")
    Optional<Match> findByRequestId(@Param("requestId") Long requestId);
}
