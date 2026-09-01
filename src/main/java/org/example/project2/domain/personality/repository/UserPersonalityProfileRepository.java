package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface UserPersonalityProfileRepository extends JpaRepository<UserPersonalityProfile, UUID> {

    @EntityGraph(attributePaths = {"styleTags", "aiKeywords"})
    Optional<UserPersonalityProfile> findByUserId(UUID userId);

    @Override
    @EntityGraph(attributePaths = {"styleTags", "aiKeywords"})
    Optional<UserPersonalityProfile> findById(UUID id);

    @Query("""
            SELECT DISTINCT profile
            FROM UserPersonalityProfile profile
            LEFT JOIN FETCH profile.styleTags
            WHERE profile.userId IN :userIds
            """)
    List<UserPersonalityProfile> findAllByUserIdIn(@Param("userIds") List<UUID> userIds);

    List<UserPersonalityProfile> findAllByAiAnalysisConsentTrueAndSelfDescriptionIsNotNull(Pageable pageable);
}
