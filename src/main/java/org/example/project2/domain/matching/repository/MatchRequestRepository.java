package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    List<MatchRequest> findAllByUserIdAndStatusIn(UUID userId, List<MatchRequestStatus> statuses);
    void deleteAllByUserIdAndStatusIn(UUID userId, List<MatchRequestStatus> statuses);
}
