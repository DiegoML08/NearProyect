package com.near.api.modules.request.repository;

import com.near.api.modules.request.entity.Request;
import com.near.api.modules.request.entity.Request.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {

    // === Búsquedas básicas ===
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findByIdWithLock(@Param("id") UUID id);

    Page<Request> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId, Pageable pageable);

    Page<Request> findByResponderIdOrderByCreatedAtDesc(UUID responderId, Pageable pageable);

    // === Búsqueda geoespacial (requests cercanas) ===
    
    @Query(value = """
        SELECT r.* FROM requests r
        WHERE r.status = 'PENDING'
        AND r.expires_at > NOW()
        AND ST_DWithin(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            r.radius_meters
        )
        ORDER BY ST_Distance(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        )
        """, nativeQuery = true)
    List<Request> findNearbyPendingRequests(
            @Param("lat") double lat,
            @Param("lng") double lng);

    // Requests cercanas para usuarios con buena reputación (modo trust activo)
    @Query(value = """
        SELECT r.* FROM requests r
        WHERE r.status = 'PENDING'
        AND r.expires_at > NOW()
        AND r.trust_mode = 'TRUST'
        AND r.trust_mode_expires_at > NOW()
        AND ST_DWithin(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            r.radius_meters
        )
        ORDER BY ST_Distance(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        )
        """, nativeQuery = true)
    List<Request> findNearbyTrustModeRequests(
            @Param("lat") double lat,
            @Param("lng") double lng);

    // Requests cercanas modo "todos" (trust mode expirado o trust_mode = ALL)
    @Query(value = """
        SELECT r.* FROM requests r
        WHERE r.status = 'PENDING'
        AND r.expires_at > NOW()
        AND (
            r.trust_mode = 'ALL' 
            OR (r.trust_mode = 'TRUST' AND r.trust_mode_expires_at <= NOW())
        )
        AND ST_DWithin(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            r.radius_meters
        )
        ORDER BY ST_Distance(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        )
        """, nativeQuery = true)
    List<Request> findNearbyAllModeRequests(
            @Param("lat") double lat,
            @Param("lng") double lng);

    // Calcular distancia entre request y punto
    @Query(value = """
        SELECT ST_Distance(
            r.location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        ) FROM requests r WHERE r.id = :requestId
        """, nativeQuery = true)
    Double calculateDistance(
            @Param("requestId") UUID requestId,
            @Param("lat") double lat,
            @Param("lng") double lng);

    // === Por estado ===
    
    List<Request> findByStatusAndExpiresAtBefore(RequestStatus status, OffsetDateTime time);

    Page<Request> findByRequesterIdAndStatusInOrderByCreatedAtDesc(
            UUID requesterId, List<RequestStatus> statuses, Pageable pageable);

    // === Estadísticas ===
    
    @Query("SELECT COUNT(r) FROM Request r WHERE r.requester.id = :userId")
    Long countByRequesterId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM Request r WHERE r.responder.id = :userId AND r.status = 'COMPLETED'")
    Long countCompletedByResponderId(@Param("userId") UUID userId);

    @Query("SELECT AVG(r.responderRating) FROM Request r WHERE r.responder.id = :userId AND r.responderRating IS NOT NULL")
    BigDecimal calculateAverageRating(@Param("userId") UUID userId);

    // === Actualización de estados ===
    
    @Modifying
    @Query("UPDATE Request r SET r.status = 'EXPIRED' WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    int expireOldRequests(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Request r SET r.viewCount = r.viewCount + 1 WHERE r.id = :requestId")
    void incrementViewCount(@Param("requestId") UUID requestId);
}
