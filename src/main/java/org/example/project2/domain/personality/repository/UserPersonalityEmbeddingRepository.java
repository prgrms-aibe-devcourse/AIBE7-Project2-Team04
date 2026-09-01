package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserPersonalityEmbeddingRepository extends JpaRepository<UserPersonalityEmbedding, Long> {

    List<UserPersonalityEmbedding> findAllByProfileUserId(UUID userId);

    void deleteAllByProfileUserId(UUID userId);

    @Query("SELECT embedding FROM UserPersonalityEmbedding embedding WHERE embedding.profile.userId IN :userIds")
    List<UserPersonalityEmbedding> findAllByProfileUserIdIn(@Param("userIds") List<UUID> userIds);
}
