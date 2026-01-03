package com.near.api.modules.auth.repository;

import com.near.api.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByAnonymousCode(String anonymousCode);

    boolean existsByEmail(String email);

    boolean existsByAnonymousCode(String anonymousCode);

    @Query("SELECT u FROM User u WHERE u.authProvider = :provider AND u.authProviderId = :providerId")
    Optional<User> findByAuthProviderAndProviderId(String provider, String providerId);
}
