package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM MatchProposal p
            JOIN FETCH p.request1 r1
            JOIN FETCH r1.user
            JOIN FETCH p.request2 r2
            JOIN FETCH r2.user
            WHERE p.id = :proposalId
            """)
    Optional<MatchProposal> findByIdForUpdate(@Param("proposalId") Long proposalId);

    @Query("SELECT p FROM MatchProposal p WHERE p.status = :status AND (p.request1.id = :requestId OR p.request2.id = :requestId)")
    Optional<MatchProposal> findActiveProposalByRequestId(@Param("requestId") Long requestId, @Param("status") MatchProposalStatus status);

    @Query("SELECT p FROM MatchProposal p WHERE p.status = 'PENDING' AND (p.request1.id = :requestId OR p.request2.id = :requestId)")
    Optional<MatchProposal> findPendingByRequestId(@Param("requestId") Long requestId);

    @Query("""
            SELECT p FROM MatchProposal p
            JOIN FETCH p.request1 r1
            JOIN FETCH p.request2 r2
            WHERE p.status = 'PENDING'
              AND (r1.user.id = :userId OR r2.user.id = :userId)
            """)
    Optional<MatchProposal> findPendingByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM MatchProposal p " +
           "WHERE p.request1.id = :r1 AND p.request2.id = :r2 AND p.createdAt >= :since")
    boolean existsProposalBetweenSince(@Param("r1") Long r1, @Param("r2") Long r2, @Param("since") Instant since);

    boolean existsByRequest1IdAndRequest2Id(Long request1Id, Long request2Id);

    @Query("""
            SELECT p
            FROM MatchProposal p
            JOIN FETCH p.request1 r1
            JOIN FETCH r1.user
            JOIN FETCH p.request2 r2
            JOIN FETCH r2.user
            WHERE p.status = 'MATCHED'
              AND r1.id = :request1Id
              AND r2.id = :request2Id
            """)
    Optional<MatchProposal> findMatchedByRequestPair(
            @Param("request1Id") Long request1Id,
            @Param("request2Id") Long request2Id
    );

    List<MatchProposal> findAllByStatusAndExpiresAtBefore(MatchProposalStatus status, Instant now);
}
