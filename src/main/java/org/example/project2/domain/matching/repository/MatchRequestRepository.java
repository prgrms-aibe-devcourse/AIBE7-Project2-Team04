package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    List<MatchRequest> findAllByStatusInAndDesiredPersonalityTextIsNotNull(
            List<MatchRequestStatus> statuses,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM MatchRequest r WHERE r.status = :status ORDER BY r.id")
    List<MatchRequest> findAllByStatus(
            @Param("status") MatchRequestStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT r
            FROM MatchRequest r
            WHERE r.status = :status
              AND r.id > :afterId
            ORDER BY r.id
            """)
    List<MatchRequest> findAllByStatusAfterId(
            @Param("status") MatchRequestStatus status,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    boolean existsByUserIdAndStatus(UUID userId, MatchRequestStatus status);

    boolean existsByUserIdAndStatusIn(UUID userId, List<MatchRequestStatus> statuses);

    Optional<MatchRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            List<MatchRequestStatus> statuses
    );

    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId AND r.user.id = :userId")
    Optional<MatchRequest> findOwnedById(
            @Param("requestId") Long requestId,
            @Param("userId") UUID userId
    );

    /**
     * 하드 필터의 1차 후보를 조회한다.
     *
     * <p>PostGIS geography 타입의 거리는 미터 단위이므로 source 요청의
     * 탐색 반경을 {@code ST_DWithin}에 그대로 전달한다. 두 요청의 반경을
     * 모두 1차 적용하고, 식사 시간과 나머지 양방향 조건은 서비스 계층에서
     * 다시 검증한다.</p>
     */
    default List<MatchRequest> findWaitingCandidates(
            MatchRequestStatus status,
            Long sourceRequestId,
            UUID sourceUserId
    ) {
        return findWaitingCandidatesByStatusName(
                status.name(),
                sourceRequestId,
                sourceUserId
        );
    }

    @Query(value = """
            SELECT candidate.*
            FROM match_requests candidate
            JOIN match_requests requester
              ON requester.id = :sourceRequestId
             AND requester.user_id = :sourceUserId
            WHERE candidate.status = :status
              AND candidate.id <> requester.id
              AND candidate.user_id <> requester.user_id
              AND ST_DWithin(requester.location, candidate.location, requester.search_radius)
              AND ST_DWithin(requester.location, candidate.location, candidate.search_radius)
            ORDER BY candidate.id
            """, nativeQuery = true)
    List<MatchRequest> findWaitingCandidatesByStatusName(
            @Param("status") String status,
            @Param("sourceRequestId") Long sourceRequestId,
            @Param("sourceUserId") UUID sourceUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 잠금 쿼리에서 컬렉션을 fetch join하면 태그 수만큼 요청 행이 중복될 수 있다.
    // 확정·예약에는 사용자와 요청 상태만 필요하므로 to-one 관계만 함께 조회한다.
    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM MatchRequest r WHERE r.id IN :requestIds ORDER BY r.id")
    List<MatchRequest> findAllByIdInForUpdate(@Param("requestIds") List<Long> requestIds);

    @EntityGraph(attributePaths = "desiredPersonalityTags")
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId")
    Optional<MatchRequest> findDetailedById(@Param("requestId") Long requestId);

    /**
     * 후보 랭킹에 필요한 요청과 희망 태그를 한 번에 조회한다.
     * 컬렉션 fetch로 생기는 중복 행은 DISTINCT로 제거한다.
     */
    @EntityGraph(attributePaths = {"user", "desiredPersonalityTags"})
    @Query("SELECT DISTINCT r FROM MatchRequest r WHERE r.id IN :requestIds")
    List<MatchRequest> findAllDetailedByIdIn(@Param("requestIds") List<Long> requestIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId AND r.user.id = :userId")
    Optional<MatchRequest> findOwnedByIdForUpdate(
            @Param("requestId") Long requestId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM MatchRequest r WHERE r.id = :requestId")
    Optional<MatchRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
