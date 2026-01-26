package com.near.api.modules.auth.repository;

import com.near.api.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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


    @Query(value = """
    SELECT u.id FROM users u
    WHERE u.is_active = true
    AND u.is_banned = false
    AND u.current_location IS NOT NULL
    AND u.last_location_update > :activeThreshold
    AND u.id != :excludeUserId
    AND ST_DWithin(
        u.current_location::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
        :radiusMeters
    )
    ORDER BY ST_Distance(
        u.current_location::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
    )
    LIMIT :maxResults
    """, nativeQuery = true)
    List<UUID> findNearbyActiveUsers(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") int radiusMeters,
            @Param("activeThreshold") OffsetDateTime activeThreshold,
            @Param("excludeUserId") UUID excludeUserId,
            @Param("maxResults") int maxResults
    );

    @Query(value = """
    SELECT u.id FROM users u
    WHERE u.is_active = true
    AND u.is_banned = false
    AND u.current_location IS NOT NULL
    AND u.last_location_update > :activeThreshold
    AND u.id != :excludeUserId
    AND u.reputation_stars >= :minReputation
    AND ST_DWithin(
        u.current_location::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
        :radiusMeters
    )
    LIMIT :maxResults
    """, nativeQuery = true)
    List<UUID> findNearbyActiveUsersForTrustMode(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") int radiusMeters,
            @Param("activeThreshold") OffsetDateTime activeThreshold,
            @Param("minReputation") BigDecimal minReputation,
            @Param("excludeUserId") UUID excludeUserId,
            @Param("maxResults") int maxResults
    );
}
