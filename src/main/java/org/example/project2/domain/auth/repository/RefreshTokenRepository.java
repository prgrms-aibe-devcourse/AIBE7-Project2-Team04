package org.example.project2.domain.auth.repository;

import jakarta.persistence.LockModeType;
import org.example.project2.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.familyId = :familyId
              and token.revokedAt is null
            """)
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt
    );

    @Query("""
            select token.familyId
            from RefreshToken token
            where token.user.id = :userId
              and token.revokedAt is null
              and token.expiresAt > :now
            group by token.familyId
            order by max(coalesce(token.lastUsedAt, token.createdAt)) asc
            """)
    List<UUID> findActiveFamilyIdsOrderByOldest(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.familyId in :familyIds
              and token.revokedAt is null
            """)
    int revokeActiveFamilies(
            @Param("familyIds") Collection<UUID> familyIds,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.replacedByToken = null
            where token.expiresAt < :cutoff
               or token.replacedByToken.id in (
                   select target.id from RefreshToken target where target.expiresAt < :cutoff
               )
            """)
    int clearReplacedByTokenForExpiredBefore(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshToken token
            where token.expiresAt < :cutoff
            """)
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
