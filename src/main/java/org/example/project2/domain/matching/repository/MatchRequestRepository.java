package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    List<MatchRequest> findAllByUserIdAndStatusIn(UUID userId, List<MatchRequestStatus> statuses);
    List<MatchRequest> findAllByUserId(UUID userId);
    void deleteAllByUserIdAndStatusIn(UUID userId, List<MatchRequestStatus> statuses);

    boolean existsByUserIdAndStatus(UUID userId, MatchRequestStatus status);

    Optional<MatchRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            List<MatchRequestStatus> statuses
    );

    @EntityGraph(attributePaths = "desiredPersonalityTags")
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId")
    Optional<MatchRequest> findDetailedById(@Param("requestId") Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId AND r.user.id = :userId")
    Optional<MatchRequest> findOwnedByIdForUpdate(
            @Param("requestId") Long requestId,
            @Param("userId") UUID userId
    );
}
