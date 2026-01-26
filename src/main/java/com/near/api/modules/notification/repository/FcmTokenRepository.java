package com.near.api.modules.notification.repository;

import com.near.api.modules.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    /**
     * Obtener todos los tokens activos de un usuario
     */
    List<FcmToken> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Buscar token específico
     */
    Optional<FcmToken> findByToken(String token);

    /**
     * Buscar token de un usuario específico
     */
    Optional<FcmToken> findByTokenAndUserId(String token, UUID userId);

    /**
     * Verificar si existe un token
     */
    boolean existsByToken(String token);

    /**
     * Eliminar token
     */
    void deleteByToken(String token);

    /**
     * Eliminar todos los tokens de un usuario
     */
    void deleteByUserId(UUID userId);

    /**
     * Desactivar token (mejor que eliminar para auditoría)
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.token = :token")
    void deactivateToken(@Param("token") String token);

    /**
     * Desactivar todos los tokens de un usuario
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.user.id = :userId")
    void deactivateAllUserTokens(@Param("userId") UUID userId);

    /**
     * Actualizar último uso del token
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.lastUsedAt = :timestamp WHERE f.token = :token")
    void updateLastUsed(@Param("token") String token, @Param("timestamp") OffsetDateTime timestamp);

    /**
     * Obtener tokens de múltiples usuarios (para notificaciones masivas)
     */
    @Query("SELECT f.token FROM FcmToken f WHERE f.user.id IN :userIds AND f.isActive = true")
    List<String> findActiveTokensByUserIds(@Param("userIds") List<UUID> userIds);

    /**
     * Contar tokens activos de un usuario
     */
    long countByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Limpiar tokens antiguos no usados (para mantenimiento)
     */
    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.lastUsedAt < :cutoffDate AND f.isActive = false")
    int deleteOldInactiveTokens(@Param("cutoffDate") OffsetDateTime cutoffDate);
}
