package org.example.project2.domain.user.repository;

import jakarta.persistence.LockModeType;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCaseAndProvider(String email, AuthProvider provider);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNickname(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
