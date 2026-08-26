package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPersonalityEmbeddingRepository extends JpaRepository<UserPersonalityEmbedding, UUID> {
}
