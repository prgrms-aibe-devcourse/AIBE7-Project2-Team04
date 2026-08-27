package org.example.project2.domain.matching.repository;

import org.example.project2.domain.matching.entity.UserMatchingPreference;
import org.example.project2.domain.matching.entity.UserMatchingPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserMatchingPreferenceRepository
        extends JpaRepository<UserMatchingPreference, UserMatchingPreferenceId> {

    @Query("SELECT p FROM UserMatchingPreference p " +
            "WHERE p.user.id = :userId ORDER BY p.id.dimension")
    List<UserMatchingPreference> findAllByUserId(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserMatchingPreference p WHERE p.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
