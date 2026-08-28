package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface UserPersonalityProfileRepository extends JpaRepository<UserPersonalityProfile, UUID> {

    @EntityGraph(attributePaths = "styleTags")
    Optional<UserPersonalityProfile> findByUserId(UUID userId);

    List<UserPersonalityProfile> findAllByAiAnalysisConsentTrueAndSelfDescriptionIsNotNull(Pageable pageable);
}
