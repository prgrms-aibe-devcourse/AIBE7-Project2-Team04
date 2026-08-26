package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.UserPersonalityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserPersonalityAnswerRepository extends JpaRepository<UserPersonalityAnswer, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from UserPersonalityAnswer answer where answer.profile.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
