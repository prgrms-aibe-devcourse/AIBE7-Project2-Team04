package org.example.project2.domain.user.repository;

import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCaseAndProvider(String email, AuthProvider provider);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNickname(String nickname);
}
