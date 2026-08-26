package org.example.project2.domain.user.repository;

import org.example.project2.domain.user.entity.UserLocationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserLocationPreferenceRepository extends JpaRepository<UserLocationPreference, UUID> {
}
